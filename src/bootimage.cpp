#include "bootimage.h"
#include "heapwalk.h"
#include "common.h"
#include "machine.h"
#include "util.h"

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
makeCodeImage(Thread* t, BootImage* image, uint8_t* code, unsigned capacity)
{
  unsigned size;
  compileThunks(t, code, &size, image);

  unsigned fixupCount = 0;
  object table = makeHashMap(t, 0, 0);
  PROTECT(t, table);

  for (Finder::Iterator it(t->m->finder); it.hasMore();) {
    unsigned nameSize;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)) {
      object c = resolveClass
        (t, makeByteArray(t, "%*s", nameSize - 5, name));
      PROTECT(t, c);
      
      for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
        object method = arrayBody(t, classMethodTable(t, c), i);
        if (methodCode(t, method)) {
          compileMethod(t, method, code, &size, capacity,
                        &table, &fixupCount);
        }
      }
    }
  }

  image->codeSize = size;

  return table;
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

HeapMap*
makeHeapImage(Thread* t, BootImage* image, uintptr_t* heap, uintptr_t* map,
              unsigned capacity)
{
  class Walker: public HeapWalker {
   public:
    Walker(Thread* t, uintptr_t* heap, uintptr_t* map, unsigned capacity):
      t(t), currentObject(0), currentOffset(0), heap(heap), map(map),
      position(0), capacity(capacity)
    { }

    void visit(object p, unsigned number) {
      if (currentObject) {
        markBit(map, (currentObject - heap) + currentOffset);
        currentObject[currentOffset] = number;
      }

      currentObject = reinterpret_cast<uintptr_t*>(p);
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

        visit(p, number);

        return number;
      } else {
        return 0;
      }
    }

    virtual void visitOld(object, unsigned number) {
      visit(0, number);
    }

    virtual void push(unsigned offset) {
      currentOffset = offset;
    }

    virtual void pop() {
      currentObject = 0;
    }

    Thread* t;
    uintptr_t* currentObject;
    unsigned currentOffset;
    uintptr_t* heap;
    uintptr_t* map;
    unsigned position;
    unsigned capacity;
  } walker(t, heap, map, capacity / BytesPerWord);

  HeapMap* table = walk(t, &walker);
  
  image->heapSize = walker.position * BytesPerWord;

  return table;
}

void
updateCodeTable(Thread* t, object codeTable, uint8_t* code, uintptr_t* codeMap,
                HeapMap* heapTable)
{
  intptr_t i = 0;
  for (HashMapIterator it(t, codeTable); it.hasMore(); ++i) {
    object mapEntry = it.next();
    intptr_t target = heapTable->find(tripleFirst(t, mapEntry));
    assert(t, target >= 0);

    for (object fixup = tripleSecond(t, mapEntry);
         fixup;
         fixup = pairSecond(t, fixup))
    {
      int32_t v = intValue(t, pairFirst(t, fixup));
      memcpy(code + v, &target, BytesPerWord);
      markBit(codeMap, v);
    }
  }
}

unsigned
offset(object a, uintptr_t* b)
{
  return reinterpret_cast<uintptr_t>(b) - reinterpret_cast<uintptr_t>(a);
}

void
writeBootImage(Thread* t, FILE* out)
{
  BootImage image;

  const unsigned CodeCapacity = 32 * 1024 * 1024;
  uint8_t* code = static_cast<uint8_t*>(t->m->heap->allocate(CodeCapacity));
  uintptr_t* codeMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(codeMapSize(CodeCapacity)));
  memset(codeMap, 0, codeMapSize(CodeCapacity));

  object codeTable = makeCodeImage(t, &image, code, CodeCapacity);

  const unsigned HeapCapacity = 32 * 1024 * 1024;
  uintptr_t* heap = static_cast<uintptr_t*>
    (t->m->heap->allocate(HeapCapacity));
  uintptr_t* heapMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(heapMapSize(HeapCapacity)));
  memset(heapMap, 0, heapMapSize(HeapCapacity));

  HeapMap* heapTable = makeHeapImage(t, &image, heap, heapMap, HeapCapacity);

  updateCodeTable(t, codeTable, code, codeMap, heapTable);

  image.magic = BootImage::Magic;

  image.codeTable = offset(codeTable, heap);

  image.loader = offset(t->m->loader, heap);
  image.bootstrapClassMap = offset(t->m->bootstrapClassMap, heap);
  image.stringMap = offset(t->m->stringMap, heap);
  image.types = offset(t->m->types, heap);
  image.jniMethodTable = offset(t->m->jniMethodTable, heap);
  image.finalizers = offset(t->m->finalizers, heap);
  image.tenuredFinalizers = offset(t->m->tenuredFinalizers, heap);
  image.finalizeQueue = offset(t->m->finalizeQueue, heap);
  image.weakReferences = offset(t->m->weakReferences, heap);
  image.tenuredWeakReferences = offset(t->m->tenuredWeakReferences, heap);

  fwrite(&image, sizeof(BootImage), 1, out);

  fwrite(heapMap, pad(heapMapSize(image.heapSize)), 1, out);
  fwrite(heap, pad(image.heapSize), 1, out);

  fwrite(codeMap, pad(codeMapSize(image.codeSize)), 1, out);
  fwrite(code, pad(image.codeSize), 1, out);
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
  Finder* f = makeFinder(s, av[0], 0);
  Processor* p = makeProcessor(s, h);
  Machine* m = new (h->allocate(sizeof(Machine))) Machine(s, h, f, p, 0, 0);
  Thread* t = p->makeThread(m, 0, 0);
  
  enter(t, Thread::ActiveState);
  enter(t, Thread::IdleState);

  writeBootImage(t, stdout);

  return 0;
}
