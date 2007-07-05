#include "common.h"
#include "system.h"
#include "heap.h"
#include "class-finder.h"
#include "stream.h"
#include "constants.h"
#include "jni-vm.h"
#include "vm.h"

#define PROTECT(thread, name)                                   \
  Thread::Protector MAKE_NAME(protector_) (thread, &name);

#define ACQUIRE(t, x) MonitorResource MAKE_NAME(monitorResource_) (t, x)

#define ACQUIRE_RAW(t, x) RawMonitorResource MAKE_NAME(monitorResource_) (t, x)

#define ENTER(t, state) StateResource MAKE_NAME(stateResource_) (t, state)

using namespace vm;

namespace {

const bool Verbose = true;
const bool Debug = false;
const bool DebugRun = false;
const bool DebugStack = false;

const uintptr_t HashTakenMark = 1;
const uintptr_t ExtendedMark = 2;

class Thread;

void (*Initializer)(Thread*, object);

void assert(Thread*, bool);
void expect(Thread*, bool);
object resolveClass(Thread*, object);
object allocate(Thread*, unsigned);
object& arrayBodyUnsafe(Thread*, object, unsigned);
void set(Thread*, object&, object);
object makeString(Thread*, const char*, ...);
object makeByteArray(Thread*, const char*, ...);

enum FieldCode {
  VoidField,
  ByteField,
  CharField,
  DoubleField,
  FloatField,
  IntField,
  LongField,
  ShortField,
  BooleanField,
  ObjectField
};

enum StackTag {
  IntTag, // must be zero
  ObjectTag
};

const int NativeLine = -1;
const int UnknownLine = -2;

const unsigned WeakReferenceFlag = 1 << 0;

class Machine {
 public:
  enum {
#include "type-enums.cpp"
  } Type;

  Machine(System* system, Heap* heap, ClassFinder* classFinder);

  ~Machine() { 
    dispose();
  }

  void dispose();

  System* system;
  Heap* heap;
  ClassFinder* classFinder;
  Thread* rootThread;
  Thread* exclusive;
  unsigned activeCount;
  unsigned liveCount;
  System::Monitor* stateLock;
  System::Monitor* heapLock;
  System::Monitor* classLock;
  System::Monitor* finalizerLock;
  System::Library* libraries;
  object classMap;
  object bootstrapClassMap;
  object builtinMap;
  object monitorMap;
  object types;
  object finalizers;
  object doomed;
  object weakReferences;
  bool unsafe;
  JNIEnvVTable jniEnvVTable;
};

class Chain {
 public:
  Chain(Chain* next): next(next) { }

  static unsigned footprint(unsigned sizeInBytes) {
    return sizeof(Chain) + sizeInBytes;
  }

  uint8_t* data() {
    return reinterpret_cast<uint8_t*>(this) + sizeof(Chain);
  }

  static void dispose(System* s, Chain* c) {
    if (c) {
      if (c->next) dispose(s, c->next);
      s->free(c);
    }
  }

  Chain* next;
};

class Thread : public JNIEnv {
 public:
  enum State {
    NoState,
    ActiveState,
    IdleState,
    ZombieState,
    ExclusiveState,
    ExitState
  };

  class Protector {
   public:
    Protector(Thread* t, object* p): t(t), p(p), next(t->protector) {
      t->protector = this;
    }

    ~Protector() {
      t->protector = next;
    }

    Thread* t;
    object* p;
    Protector* next;
  };

  static const unsigned HeapSizeInBytes = 64 * 1024;
  static const unsigned StackSizeInBytes = 64 * 1024;

  static const unsigned HeapSizeInWords = HeapSizeInBytes / BytesPerWord;
  static const unsigned StackSizeInWords = StackSizeInBytes / BytesPerWord;

  Thread(Machine* m);

  void dispose();

  Machine* vm;
  Thread* next;
  Thread* child;
  State state;
  object thread;
  object frame;
  object code;
  object exception;
  unsigned ip;
  unsigned sp;
  unsigned heapIndex;
  Protector* protector;
  Chain* chain;
  uintptr_t stack[StackSizeInWords];
  object heap[HeapSizeInWords];
};

inline object
objectClass(Thread*, object o)
{
  return mask(cast<object>(o, 0));
}

#include "type-declarations.cpp"
#include "type-constructors.cpp"

void enter(Thread* t, Thread::State state);

class StateResource {
 public:
  StateResource(Thread* t, Thread::State state): t(t), oldState(t->state) {
    enter(t, state);
  }

  ~StateResource() { enter(t, oldState); }

 private:
  Thread* t;
  Thread::State oldState;
};

class MonitorResource {
 public:
  MonitorResource(Thread* t, System::Monitor* m): t(t), m(m) {
    if (not m->tryAcquire(t)) {
      ENTER(t, Thread::IdleState);
      m->acquire(t);
    }
  }

  ~MonitorResource() { m->release(t); }

 private:
  Thread* t;
  System::Monitor* m;
};

class RawMonitorResource {
 public:
  RawMonitorResource(Thread* t, System::Monitor* m): t(t), m(m) {
    m->acquire(t);
  }

  ~RawMonitorResource() { m->release(t); }

 private:
  Thread* t;
  System::Monitor* m;
};

inline void NO_RETURN
abort(Thread* t)
{
  abort(t->vm->system);
}

inline void
assert(Thread* t, bool v)
{
  assert(t->vm->system, v);
}

inline void
expect(Thread* t, bool v)
{
  expect(t->vm->system, v);
}

uint32_t
hash(const int8_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) h = (h * 31) + s[i];
  return h;  
}

inline bool
objectExtended(Thread*, object o)
{
  return (cast<uintptr_t>(o, 0) & (~PointerMask)) == ExtendedMark;
}

inline uintptr_t&
extendedWord(Thread* t, object o, unsigned baseSize)
{
  assert(t, objectExtended(t, o));
  return cast<uintptr_t>(o, baseSize * BytesPerWord);
}

unsigned
baseSize(Thread* t, object o, object class_)
{
  return divide(classFixedSize(t, class_), BytesPerWord)
    + divide(classArrayElementSize(t, class_)
             * cast<uint32_t>(o, classFixedSize(t, class_) - 4),
             BytesPerWord);
}

unsigned
extendedSize(Thread* t, object o, unsigned baseSize)
{
  return baseSize + objectExtended(t, o);
}

inline bool
hashTaken(Thread*, object o)
{
  return (cast<uintptr_t>(o, 0) & (~PointerMask)) == HashTakenMark;
}

inline void
markHashTaken(Thread* t, object o)
{
  assert(t, not objectExtended(t, o));
  cast<uintptr_t>(o, 0) |= HashTakenMark;
}

inline uint32_t
takeHash(Thread*, object o)
{
  return reinterpret_cast<uintptr_t>(o) / BytesPerWord;
}

inline uint32_t
objectHash(Thread* t, object o)
{
  if (objectExtended(t, o)) {
    return extendedWord(t, o, baseSize(t, o, objectClass(t, o)));
  } else {
    markHashTaken(t, o);
    return takeHash(t, o);
  }
}

inline bool
objectEqual(Thread*, object a, object b)
{
  return a == b;
}

inline uint32_t
referenceHash(Thread* t, object o)
{
  return objectHash(t, jreferenceTarget(t, o));
}

inline bool
referenceEqual(Thread* t, object a, object b)
{
  return a == jreferenceTarget(t, b);
}

inline uint32_t
byteArrayHash(Thread* t, object array)
{
  return hash(&byteArrayBody(t, array, 0), byteArrayLength(t, array));
}

bool
byteArrayEqual(Thread* t, object a, object b)
{
  return a == b or
    ((byteArrayLength(t, a) == byteArrayLength(t, b)) and
     memcmp(&byteArrayBody(t, a, 0), &byteArrayBody(t, b, 0),
            byteArrayLength(t, a)) == 0);
}

bool
intArrayEqual(Thread* t, object a, object b)
{
  return a == b or
    ((intArrayLength(t, a) == intArrayLength(t, b)) and
     memcmp(&intArrayBody(t, a, 0), &intArrayBody(t, b, 0),
            intArrayLength(t, a) * 4) == 0);
}

inline uint32_t
methodHash(Thread* t, object method)
{
  return byteArrayHash(t, methodName(t, method))
    ^ byteArrayHash(t, methodSpec(t, method));
}

bool
methodEqual(Thread* t, object a, object b)
{
  return a == b or
    (byteArrayEqual(t, methodName(t, a), methodName(t, b)) and
     byteArrayEqual(t, methodSpec(t, a), methodSpec(t, b)));
}

object
hashMapFindNode(Thread* t, object map, object key,
                uint32_t (*hash)(Thread*, object),
                bool (*equal)(Thread*, object, object))
{
  object array = hashMapArray(t, map);
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    object n = arrayBody(t, array, index);
    while (n) {
      if (equal(t, key, tripleFirst(t, n))) {
        return n;
      }
      
      n = tripleThird(t, n);
    }
  }
  return 0;
}

inline object
hashMapFind(Thread* t, object map, object key,
            uint32_t (*hash)(Thread*, object),
            bool (*equal)(Thread*, object, object))
{
  object n = hashMapFindNode(t, map, key, hash, equal);
  return (n ? tripleSecond(t, n) : 0);
}

void
hashMapResize(Thread* t, object map, uint32_t (*hash)(Thread*, object),
              unsigned size)
{
  PROTECT(t, map);

  object oldArray = hashMapArray(t, map);
  unsigned oldLength = (oldArray ? arrayLength(t, oldArray) : 0);
  PROTECT(t, oldArray);

  unsigned newLength = nextPowerOfTwo(size);
  object newArray = makeArray(t, newLength, true);

  if (oldArray) {
    for (unsigned i = 0; i < oldLength; ++i) {
      object next;
      for (object p = arrayBody(t, oldArray, i); p; p = next) {
        next = tripleThird(t, p);

        object key = tripleFirst(t, p);
        unsigned index = hash(t, key) & (newLength - 1);
        object n = arrayBody(t, newArray, index);

        set(t, tripleThird(t, p), n);
        set(t, arrayBody(t, newArray, index), p);
      }
    }
  }
  
  set(t, hashMapArray(t, map), newArray);
}

void
hashMapInsert(Thread* t, object map, object key, object value,
              uint32_t (*hash)(Thread*, object))
{
  object array = hashMapArray(t, map);
  PROTECT(t, array);

  ++ hashMapSize(t, map);

  if (array == 0 or hashMapSize(t, map) >= arrayLength(t, array) * 2) { 
    PROTECT(t, map);
    PROTECT(t, key);
    PROTECT(t, value);

    hashMapResize(t, map, hash, array ? arrayLength(t, array) * 2 : 16);
    array = hashMapArray(t, map);
  }

  unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
  object n = arrayBody(t, array, index);

  n = makeTriple(t, key, value, n);

  set(t, arrayBody(t, array, index), n);
}

inline bool
hashMapInsertOrReplace(Thread* t, object map, object key, object value,
                       uint32_t (*hash)(Thread*, object),
                       bool (*equal)(Thread*, object, object))
{
  object n = hashMapFindNode(t, map, key, hash, equal);
  if (n == 0) {
    hashMapInsert(t, map, key, value, hash);
    return true;
  } else {
    set(t, tripleSecond(t, n), value);
    return false;
  }
}

object
hashMapRemove(Thread* t, object map, object key,
              uint32_t (*hash)(Thread*, object),
              bool (*equal)(Thread*, object, object))
{
  object array = hashMapArray(t, map);
  object o = 0;
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    object n = arrayBody(t, array, index);
    object p = 0;
    while (n) {
      if (equal(t, key, tripleFirst(t, n))) {
        o = tripleFirst(t, n);
        if (p) {
          set(t, tripleThird(t, p), tripleThird(t, n));
        } else {
          set(t, arrayBody(t, array, index), tripleThird(t, n));
        }
      }
      
      p = n;
      n = tripleThird(t, n);
    }
  }

  if (hashMapSize(t, map) <= arrayLength(t, array) / 3) { 
    hashMapResize(t, map, hash, arrayLength(t, array) / 2);
  }

  return o;
}

object
hashMapIterator(Thread* t, object map)
{
  object array = hashMapArray(t, map);
  if (array) {
    for (unsigned i = 0; i < arrayLength(t, array); ++i) {
      if (arrayBody(t, array, i)) {
        return makeHashMapIterator(t, map, arrayBody(t, array, i), i + 1);
      }
    }
  }
  return 0;
}

object
hashMapIteratorNext(Thread* t, object it)
{
  object map = hashMapIteratorMap(t, it);
  object node = hashMapIteratorNode(t, it);
  unsigned index = hashMapIteratorIndex(t, it);

  if (tripleThird(t, node)) {
    return makeHashMapIterator(t, map, tripleThird(t, node), index + 1);
  } else {
    object array = hashMapArray(t, map);
    for (unsigned i = index; i < arrayLength(t, array); ++i) {
      if (arrayBody(t, array, i)) {
        return makeHashMapIterator(t, map, arrayBody(t, array, i), i + 1);
      }
    }
    return 0;
  }  
}

void
listAppend(Thread* t, object list, object value)
{
  PROTECT(t, list);

  ++ listSize(t, list);
  
  object p = makePair(t, value, 0);
  if (listFront(t, list)) {
    set(t, pairSecond(t, listRear(t, list)), p);
  } else {
    set(t, listFront(t, list), p);
  }
  set(t, listRear(t, list), p);
}

inline void
pushObject(Thread* t, object o)
{
  if (DebugStack) {
    fprintf(stderr, "push object %p at %d\n", o, t->sp);
  }

  t->stack[(t->sp * 2)    ] = ObjectTag;
  t->stack[(t->sp * 2) + 1] = reinterpret_cast<uintptr_t>(o);
  ++ t->sp;
}

inline void
pushInt(Thread* t, uint32_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push int %d at %d\n", v, t->sp);
  }

  t->stack[(t->sp * 2)    ] = IntTag;
  t->stack[(t->sp * 2) + 1] = v;
  ++ t->sp;
}

inline void
pushLong(Thread* t, uint64_t v)
{
  if (DebugStack) {
    fprintf(stderr, "push long %lld at %d\n", v, t->sp);
  }

  pushInt(t, v >> 32);
  pushInt(t, v & 0xFF);
}

inline object
popObject(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop object %p at %d\n",
            reinterpret_cast<object>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 1);
  }

  assert(t, t->stack[(t->sp - 1) * 2] == ObjectTag);
  return reinterpret_cast<object>(t->stack[((-- t->sp) * 2) + 1]);
}

inline uint32_t
popInt(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop int %d at %d\n",
            t->stack[((t->sp - 1) * 2) + 1],
            t->sp - 1);
  }

  assert(t, t->stack[(t->sp - 1) * 2] == IntTag);
  return t->stack[((-- t->sp) * 2) + 1];
}

inline uint64_t
popLong(Thread* t)
{
  if (DebugStack) {
    fprintf(stderr, "pop long %lld at %d\n",
            (static_cast<uint64_t>(t->stack[((t->sp - 2) * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((t->sp - 1) * 2) + 1]),
            t->sp - 2);
  }

  uint64_t a = popInt(t);
  uint64_t b = popInt(t);
  return (b << 32) | a;
}

inline object
peekObject(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek object %p at %d\n",
            reinterpret_cast<object>(t->stack[(index * 2) + 1]),
            index);
  }

  assert(t, t->stack[index * 2] == ObjectTag);
  return *reinterpret_cast<object*>(t->stack + (index * 2) + 1);
}

inline uint32_t
peekInt(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek int %d at %d\n",
            t->stack[(index * 2) + 1],
            index);
  }

  assert(t, t->stack[index * 2] == IntTag);
  return t->stack[(index * 2) + 1];
}

inline uint64_t
peekLong(Thread* t, unsigned index)
{
  if (DebugStack) {
    fprintf(stderr, "peek long %lld at %d\n",
            (static_cast<uint64_t>(t->stack[(index * 2) + 1]) << 32)
            | static_cast<uint64_t>(t->stack[((index + 1) * 2) + 1]),
            index);
  }

  return (static_cast<uint64_t>(peekInt(t, index)) << 32)
    | static_cast<uint64_t>(peekInt(t, index + 1));
}

inline void
pokeObject(Thread* t, unsigned index, object value)
{
  if (DebugStack) {
    fprintf(stderr, "poke object %p at %d\n", value, index);
  }

  t->stack[index * 2] = ObjectTag;
  t->stack[(index * 2) + 1] = reinterpret_cast<uintptr_t>(value);
}

inline void
pokeInt(Thread* t, unsigned index, uint32_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke int %d at %d\n", value, index);
  }

  t->stack[index * 2] = IntTag;
  t->stack[(index * 2) + 1] = value;
}

inline void
pokeLong(Thread* t, unsigned index, uint64_t value)
{
  if (DebugStack) {
    fprintf(stderr, "poke long %lld at %d\n", value, index);
  }

  pokeInt(t, index, value >> 32);
  pokeInt(t, index + 2, value & 0xFF);
}

inline object*
pushReference(Thread* t, object o)
{
  expect(t, t->sp + 1 < Thread::StackSizeInWords / 2);
  pushObject(t, o);
  return reinterpret_cast<object*>(t->stack + ((t->sp - 1) * 2) + 1);
}

void
Thread::dispose()
{
  Chain::dispose(vm->system, chain);
  
  for (Thread* c = child; c; c = c->next) {
    c->dispose();
  }
}

void
visitRoots(Thread* t, Heap::Visitor* v)
{
  t->heapIndex = 0;

  v->visit(&(t->thread));
  v->visit(&(t->frame));
  v->visit(&(t->code));
  v->visit(&(t->exception));

  for (unsigned i = 0; i < t->sp; ++i) {
    if (t->stack[i * 2] == ObjectTag) {
      v->visit(reinterpret_cast<object*>(t->stack + (i * 2) + 1));
    }
  }

  for (Thread::Protector* p = t->protector; p; p = p->next) {
    v->visit(p->p);
  }

  for (Thread* c = t->child; c; c = c->next) {
    visitRoots(c, v);
  }
}

void
postCollect(Thread* t)
{
  Chain::dispose(t->vm->system, t->chain);
  t->chain = 0;

  for (Thread* c = t->child; c; c = c->next) {
    postCollect(c);
  }
}

void
collect(Machine* m, Heap::CollectionType type)
{
  class Client: public Heap::Client {
   public:
    Client(Machine* m): m(m) { }

    virtual void visitRoots(Heap::Visitor* v) {
      v->visit(&(m->classMap));
      v->visit(&(m->bootstrapClassMap));
      v->visit(&(m->builtinMap));
      v->visit(&(m->monitorMap));
      v->visit(&(m->types));

      for (Thread* t = m->rootThread; t; t = t->next) {
        ::visitRoots(t, v);
      }

      Thread* t = m->rootThread;
      for (object* f = &(m->finalizers); *f;) {
        object o = finalizerTarget(t, *f);
        if (m->heap->follow(o) == o) {
          // object has not been collected
          object x = *f;
          *f = finalizerNext(t, x);
          finalizerNext(t, x) = m->doomed;
          m->doomed = x;
        } else {
          f = &finalizerNext(t, *f);
        }
      }

      for (object* f = &(m->finalizers); *f; f = &finalizerNext(t, *f)) {
        v->visit(f);
      }

      for (object* f = &(m->doomed); *f; f = &finalizerNext(t, *f)) {
        v->visit(f);
      }

      for (object p = m->weakReferences; p;) {
        object o = jreferenceTarget(t, p);
        object followed = m->heap->follow(o);
        if (followed == o) {
          // object has not been collected
          jreferenceTarget(t, p) = 0;
        } else {
          jreferenceTarget(t, p) = followed;
        }

        object last = p;
        p = weakReferenceNext(t, p);
        weakReferenceNext(t, last) = 0;
      }
    }

    virtual unsigned sizeInWords(object o) {
      Thread* t = m->rootThread;

      o = m->heap->follow(mask(o));

      return extendedSize
        (t, o, baseSize(t, o, m->heap->follow(objectClass(t, o))));
    }

    virtual unsigned copiedSizeInWords(object o) {
      Thread* t = m->rootThread;

      o = m->heap->follow(mask(o));

      unsigned n = baseSize(t, o, m->heap->follow(objectClass(t, o)));

      if (objectExtended(t, o) or hashTaken(t, o)) {
        ++ n;
      }

      return n;
    }

    virtual void copy(object o, object dst) {
      Thread* t = m->rootThread;

      o = m->heap->follow(mask(o));
      object class_ = m->heap->follow(objectClass(t, o));

      unsigned base = baseSize(t, o, class_);
      unsigned n = extendedSize(t, o, base);

      memcpy(dst, o, n * BytesPerWord);

      if (hashTaken(t, o)) {
        extendedWord(t, dst, base) = takeHash(t, o);
        cast<uintptr_t>(dst, 0) &= PointerMask;
        cast<uintptr_t>(dst, 0) |= ExtendedMark;
      }

      if (classVmFlags(t, class_) & WeakReferenceFlag) {
        weakReferenceNext(t, dst) = m->weakReferences;
        m->weakReferences = dst;
      }
    }

    virtual void walk(void* p, Heap::Walker* w) {
      Thread* t = m->rootThread;

      p = m->heap->follow(mask(p));
      object class_ = m->heap->follow(objectClass(t, p));
      object objectMask = m->heap->follow(classObjectMask(t, class_));

      if (objectMask) {
//         fprintf(stderr, "p: %p; class: %p; mask: %p; mask length: %d\n",
//                 p, class_, objectMask, intArrayLength(t, objectMask));

        unsigned vmFlags = classVmFlags(t, class_);
        unsigned fixedSize = classFixedSize(t, class_);
        unsigned arrayElementSize = classArrayElementSize(t, class_);
        unsigned arrayLength
          = (arrayElementSize ? cast<uint32_t>(p, fixedSize - 4) : 0);

        int mask[intArrayLength(t, objectMask)];
        memcpy(mask, &intArrayBody(t, objectMask, 0),
               intArrayLength(t, objectMask) * 4);

//         fprintf
//           (stderr,
//            "fixed size: %d; array length: %d; element size: %d; mask: %x\n",
//            fixedSize, arrayLength, arrayElementSize, mask[0]);

        unsigned fixedSizeInWords = divide(fixedSize, BytesPerWord);
        unsigned arrayElementSizeInWords
          = divide(arrayElementSize, BytesPerWord);

        for (unsigned i = 0; i < fixedSizeInWords; ++i) {
          if ((i != 1 or (vmFlags & WeakReferenceFlag) == 0)
              and mask[wordOf(i)] & (static_cast<uintptr_t>(1) << bitOf(i)))
          {
            if (not w->visit(i)) {
              return;
            }
          }
        }

        bool arrayObjectElements = false;
        for (unsigned j = 0; j < arrayElementSizeInWords; ++j) {
          unsigned k = fixedSizeInWords + j;
          if (mask[wordOf(k)] & (static_cast<uintptr_t>(1) << bitOf(k))) {
            arrayObjectElements = true;
            break;
          }
        }

        if (arrayObjectElements) {
          for (unsigned i = 0; i < arrayLength; ++i) {
            for (unsigned j = 0; j < arrayElementSizeInWords; ++j) {
              unsigned k = fixedSizeInWords + j;
              if (mask[wordOf(k)] & (static_cast<uintptr_t>(1) << bitOf(k))) {
                if (not w->visit
                    (fixedSizeInWords + (i * arrayElementSizeInWords) + j))
                {
                  return;
                }
              }
            }
          }
        }
      } else {
        w->visit(0);
      }
    }
    
   private:
    Machine* m;
  } it(m);

  m->unsafe = true;
  m->heap->collect(type, &it);
  m->unsafe = false;

  postCollect(m->rootThread);

  Thread* t = m->rootThread;
  for (object f = m->doomed; f; f = tripleThird(t, f)) {
    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
  }
  m->doomed = 0;

  m->weakReferences = 0;
}

void
enter(Thread* t, Thread::State s)
{
  if (s == t->state) return;

  ACQUIRE_RAW(t, t->vm->stateLock);

  switch (s) {
  case Thread::ExclusiveState: {
    assert(t, t->state == Thread::ActiveState);

    while (t->vm->exclusive) {
      // another thread got here first.
      enter(t, Thread::IdleState);
      enter(t, Thread::ActiveState);
    }

    t->state = Thread::ExclusiveState;
    t->vm->exclusive = t;
      
    while (t->vm->activeCount > 1) {
      t->vm->stateLock->wait(t, 0);
    }
  } break;

  case Thread::IdleState:
  case Thread::ZombieState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);
      t->vm->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    -- t->vm->activeCount;
    if (s == Thread::ZombieState) {
      -- t->vm->liveCount;
    }
    t->state = s;

    t->vm->stateLock->notifyAll(t);
  } break;

  case Thread::ActiveState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);

      t->state = s;
      t->vm->exclusive = 0;

      t->vm->stateLock->notifyAll(t);
    } break;

    case Thread::NoState:
    case Thread::IdleState: {
      while (t->vm->exclusive) {
        t->vm->stateLock->wait(t, 0);
      }

      ++ t->vm->activeCount;
      if (t->state == Thread::NoState) {
        ++ t->vm->liveCount;
      }
      t->state = s;
    } break;

    default: abort(t);
    }
  } break;

  case Thread::ExitState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->vm->exclusive == t);
      t->vm->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }
      
    -- t->vm->activeCount;
    t->state = s;

    while (t->vm->liveCount > 1) {
      t->vm->stateLock->wait(t, 0);
    }
  } break;

  default: abort(t);
  }
}

inline object
allocateLarge(Thread* t, unsigned sizeInBytes)
{
  void* p = t->vm->system->allocate(Chain::footprint(sizeInBytes));
  t->chain = new (p) Chain(t->chain);
  return t->chain->data();
}

inline object
allocateSmall(Thread* t, unsigned sizeInBytes)
{
  object o = t->heap + t->heapIndex;
  t->heapIndex += divide(sizeInBytes, BytesPerWord);
  return o;
}

object
allocate2(Thread* t, unsigned sizeInBytes)
{
  if (sizeInBytes > Thread::HeapSizeInBytes and t->chain == 0) {
    return allocateLarge(t, sizeInBytes);
  }

  ACQUIRE_RAW(t, t->vm->stateLock);

  while (t->vm->exclusive) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    enter(t, Thread::IdleState);
    enter(t, Thread::ActiveState);
  }

  if (t->heapIndex + divide(sizeInBytes, BytesPerWord)
      >= Thread::HeapSizeInWords)
  {
    enter(t, Thread::ExclusiveState);
    collect(t->vm, Heap::MinorCollection);
    enter(t, Thread::ActiveState);
  }

  if (sizeInBytes > Thread::HeapSizeInBytes) {
    return allocateLarge(t, sizeInBytes);
  } else {
    return allocateSmall(t, sizeInBytes);
  }
}

inline object
allocate(Thread* t, unsigned sizeInBytes)
{
  if (UNLIKELY(t->heapIndex + divide(sizeInBytes, BytesPerWord)
               >= Thread::HeapSizeInWords
               or t->vm->exclusive))
  {
    return allocate2(t, sizeInBytes);
  } else {
    return allocateSmall(t, sizeInBytes);
  }
}

inline void
set(Thread* t, object& target, object value)
{
  target = value;
  if (t->vm->heap->needsMark(&target)) {
    ACQUIRE_RAW(t, t->vm->heapLock);
    t->vm->heap->mark(&target);
  }
}

inline object
make(Thread* t, object class_)
{
  PROTECT(t, class_);
  unsigned sizeInBytes = pad(classFixedSize(t, class_));
  object instance = allocate(t, sizeInBytes);
  *static_cast<object*>(instance) = class_;
  memset(static_cast<object*>(instance) + sizeof(object), 0,
         sizeInBytes - sizeof(object));
  return instance;
}

void
addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object))
{
  PROTECT(t, target);

  ACQUIRE(t, t->vm->finalizerLock);

  object p = makePointer(t, reinterpret_cast<void*>(finalize));
  t->vm->finalizers = makeTriple(t, target, p, t->vm->finalizers);
}

void
removeMonitor(Thread* t, object o)
{
  hashMapRemove(t, t->vm->monitorMap, o, objectHash, referenceEqual);
}

System::Monitor*
objectMonitor(Thread* t, object o)
{
  object p = hashMapFind(t, t->vm->monitorMap, o, objectHash, referenceEqual);

  if (p) {
    return static_cast<System::Monitor*>(pointerValue(t, p));
  } else {
    PROTECT(t, o);

    ENTER(t, Thread::ExclusiveState);

    System::Monitor* m;
    System::Status s = t->vm->system->make(&m);
    expect(t, t->vm->system->success(s));

    p = makePointer(t, m);
    PROTECT(t, p);

    object wr = makeWeakReference(t, o, 0);

    hashMapInsert(t, t->vm->monitorMap, wr, p, referenceHash);

    addFinalizer(t, o, removeMonitor);

    return m;
  }
}

object
makeByteArray(Thread* t, const char* format, va_list a)
{
  static const unsigned Size = 256;
  char buffer[Size];
  
  vsnprintf(buffer, Size - 1, format, a);

  object s = makeByteArray(t, strlen(buffer) + 1, false);
  memcpy(&byteArrayBody(t, s, 0), buffer, byteArrayLength(t, s));

  return s;
}

object
makeByteArray(Thread* t, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  object s = makeByteArray(t, format, a);
  va_end(a);

  return s;
}

object
makeString(Thread* t, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  object s = makeByteArray(t, format, a);
  va_end(a);

  return makeString(t, s, 0, byteArrayLength(t, s), 0);
}

object
makeTrace(Thread* t, object frame)
{
  PROTECT(t, frame);

  unsigned count = 0;
  for (object f = frame; f; f = frameNext(t, f)) {
    ++ count;
  }

  object trace = makeObjectArray
    (t, arrayBody(t, t->vm->types, Machine::StackTraceElementType),
     count, true);
  PROTECT(t, trace);

  unsigned index = 0;
  for (object f = frame; f; f = frameNext(t, f)) {
    object e = makeStackTraceElement(t, frameMethod(t, f), frameIp(t, f));
    set(t, objectArrayBody(t, trace, index++), e);
  }

  return trace;
}

object
makeTrace(Thread* t)
{
  frameIp(t, t->frame) = t->ip;
  return makeTrace(t, t->frame);
}

object
makeRuntimeException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeRuntimeException(t, message, trace, 0);
}

object
makeArrayIndexOutOfBoundsException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeArrayIndexOutOfBoundsException(t, message, trace, 0);
}

object
makeNegativeArrayStoreException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNegativeArrayStoreException(t, message, trace, 0);
}

object
makeClassCastException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeClassCastException(t, message, trace, 0);
}

object
makeClassNotFoundException(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeClassNotFoundException(t, message, trace, 0);
}

object
makeNullPointerException(Thread* t)
{
  return makeNullPointerException(t, 0, makeTrace(t), 0);
}

object
makeStackOverflowError(Thread* t)
{
  return makeStackOverflowError(t, 0, makeTrace(t), 0);
}

object
makeNoSuchFieldError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNoSuchFieldError(t, message, trace, 0);
}

object
makeNoSuchMethodError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeNoSuchMethodError(t, message, trace, 0);
}

object
makeUnsatisfiedLinkError(Thread* t, object message)
{
  PROTECT(t, message);
  object trace = makeTrace(t);
  return makeUnsatisfiedLinkError(t, message, trace, 0);
}

unsigned
fieldCode(Thread* t, unsigned javaCode)
{
  switch (javaCode) {
  case 'B':
    return ByteField;
  case 'C':
    return CharField;
  case 'D':
    return DoubleField;
  case 'F':
    return FloatField;
  case 'I':
    return IntField;
  case 'J':
    return LongField;
  case 'S':
    return ShortField;
  case 'V':
    return VoidField;
  case 'Z':
    return BooleanField;
  case 'L':
  case '[':
    return ObjectField;

  default: abort(t);
  }
}

unsigned
fieldType(Thread* t, unsigned code)
{
  switch (code) {
  case VoidField:
    return VOID_TYPE;
  case ByteField:
  case BooleanField:
    return INT8_TYPE;
  case CharField:
  case ShortField:
    return INT16_TYPE;
  case DoubleField:
    return DOUBLE_TYPE;
  case FloatField:
    return FLOAT_TYPE;
  case IntField:
    return INT32_TYPE;
  case LongField:
    return INT64_TYPE;
  case ObjectField:
    return POINTER_TYPE;

  default: abort(t);
  }
}

unsigned
primitiveSize(Thread* t, unsigned code)
{
  switch (code) {
  case VoidField:
    return 0;
  case ByteField:
  case BooleanField:
    return 1;
  case CharField:
  case ShortField:
    return 2;
  case FloatField:
  case IntField:
    return 4;
  case DoubleField:
  case LongField:
    return 8;

  default: abort(t);
  }
}

inline void
setStatic(Thread* t, object field, object value)
{
  set(t, arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                   fieldOffset(t, field)), value);
}

bool
instanceOf(Thread* t, object class_, object o)
{
  if (o == 0) {
    return false;
  }

  if (classFlags(t, class_) & ACC_INTERFACE) {
    for (object oc = objectClass(t, o); oc; oc = classSuper(t, oc)) {
      object itable = classInterfaceTable(t, oc);
      for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
        if (arrayBody(t, itable, i) == class_) {
          return true;
        }
      }
    }
  } else {
    for (object oc = objectClass(t, o); oc; oc = classSuper(t, oc)) {
      if (oc == class_) {
        return true;
      }
    }
  }

  return false;
}

object
findInterfaceMethod(Thread* t, object method, object o)
{
  object interface = methodClass(t, method);
  object itable = classInterfaceTable(t, objectClass(t, o));
  for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
    if (arrayBody(t, itable, i) == interface) {
      return arrayBody(t, arrayBody(t, itable, i + 1),
                       methodOffset(t, method));
    }
  }
  abort(t);
}

inline object
findMethod(Thread* t, object method, object class_)
{
  return arrayBody(t, classVirtualTable(t, class_), 
                   methodOffset(t, method));
}

inline object
findVirtualMethod(Thread* t, object method, object o)
{
  return findMethod(t, method, objectClass(t, o));
}

bool
isSuperclass(Thread* t, object class_, object base)
{
  for (object oc = classSuper(t, base); oc; oc = classSuper(t, oc)) {
    if (oc == class_) {
      return true;
    }
  }
  return false;
}

inline int
strcmp(const int8_t* a, const int8_t* b)
{
  return ::strcmp(reinterpret_cast<const char*>(a),
                  reinterpret_cast<const char*>(b));
}

inline bool
isSpecialMethod(Thread* t, object method, object class_)
{
  return (classFlags(t, class_) & ACC_SUPER)
    and strcmp(reinterpret_cast<const int8_t*>("<init>"), 
               &byteArrayBody(t, methodName(t, method), 0)) != 0
    and isSuperclass(t, methodClass(t, method), class_);
}

object
find(Thread* t, object class_, object table, object reference,
     object& (*name)(Thread*, object),
     object& (*spec)(Thread*, object),
     object (*makeError)(Thread*, object))
{
  object n = referenceName(t, reference);
  object s = referenceSpec(t, reference);
  for (unsigned i = 0; i < arrayLength(t, table); ++i) {
    object o = arrayBody(t, table, i);

    if (strcmp(&byteArrayBody(t, name(t, o), 0),
               &byteArrayBody(t, n, 0)) == 0 and
        strcmp(&byteArrayBody(t, spec(t, o), 0),
               &byteArrayBody(t, s, 0)) == 0)
    {
      return o;
    }               
  }

  object message = makeString
    (t, "%s:%s not found in %s",
     &byteArrayBody(t, n, 0),
     &byteArrayBody(t, s, 0),
     &byteArrayBody(t, className(t, class_), 0));
  t->exception = makeError(t, message);
  return 0;
}

inline object
findFieldInClass(Thread* t, object class_, object reference)
{
  return find(t, class_, classFieldTable(t, class_), reference, fieldName,
              fieldSpec, makeNoSuchFieldError);
}

inline object
findMethodInClass(Thread* t, object class_, object reference)
{
  return find(t, class_, classMethodTable(t, class_), reference, methodName,
              methodSpec, makeNoSuchMethodError);
}

object
parsePool(Thread* t, Stream& s)
{
  unsigned poolCount = s.read2() - 1;
  object pool = makeArray(t, poolCount, true);

  PROTECT(t, pool);

  for (unsigned i = 0; i < poolCount; ++i) {
    unsigned c = s.read1();

    switch (c) {
    case CONSTANT_Integer: {
      object value = makeInt(t, s.read4());
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Float: {
      object value = makeFloat(t, s.readFloat());
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Long: {
      object value = makeLong(t, s.read8());
      set(t, arrayBody(t, pool, i), value);
      ++i;
    } break;

    case CONSTANT_Double: {
      object value = makeLong(t, s.readDouble());
      set(t, arrayBody(t, pool, i), value);
      ++i;
    } break;

    case CONSTANT_Utf8: {
      unsigned length = s.read2();
      object value = makeByteArray(t, length + 1, false);
      s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, value, 0)), length);
      byteArrayBody(t, value, length) = 0;
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Class: {
      object value = makeIntArray(t, 2, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_String: {
      object value = makeIntArray(t, 2, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_NameAndType: {
      object value = makeIntArray(t, 3, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      intArrayBody(t, value, 2) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    case CONSTANT_Fieldref:
    case CONSTANT_Methodref:
    case CONSTANT_InterfaceMethodref: {
      object value = makeIntArray(t, 3, false);
      intArrayBody(t, value, 0) = c;
      intArrayBody(t, value, 1) = s.read2();
      intArrayBody(t, value, 2) = s.read2();
      set(t, arrayBody(t, pool, i), value);
    } break;

    default: abort(t);
    }
  }

  for (unsigned i = 0; i < poolCount; ++i) {
    object o = arrayBody(t, pool, i);
    if (o and objectClass(t, o)
        == arrayBody(t, t->vm->types, Machine::IntArrayType))
    {
      switch (intArrayBody(t, o, 0)) {
      case CONSTANT_Class: {
        set(t, arrayBody(t, pool, i),
            arrayBody(t, pool, intArrayBody(t, o, 1) - 1));
      } break;

      case CONSTANT_String: {
        object bytes = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object value = makeString(t, bytes, 0, byteArrayLength(t, bytes), 0);
        set(t, arrayBody(t, pool, i), value);
      } break;

      case CONSTANT_NameAndType: {
        object name = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object type = arrayBody(t, pool, intArrayBody(t, o, 2) - 1);
        object value = makePair(t, name, type);
        set(t, arrayBody(t, pool, i), value);
      } break;
      }
    }
  }

  for (unsigned i = 0; i < poolCount; ++i) {
    object o = arrayBody(t, pool, i);
    if (o and objectClass(t, o)
        == arrayBody(t, t->vm->types, Machine::IntArrayType))
    {
      switch (intArrayBody(t, o, 0)) {
      case CONSTANT_Fieldref:
      case CONSTANT_Methodref:
      case CONSTANT_InterfaceMethodref: {
        object c = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object nameAndType = arrayBody(t, pool, intArrayBody(t, o, 2) - 1);
        object value = makeReference
          (t, c, pairFirst(t, nameAndType), pairSecond(t, nameAndType));
        set(t, arrayBody(t, pool, i), value);
      } break;
      }
    }
  }

  return pool;
}

void
addInterfaces(Thread* t, object class_, object map)
{
  object table = classInterfaceTable(t, class_);
  if (table) {
    unsigned increment = 2;
    if (classFlags(t, class_) & ACC_INTERFACE) {
      increment = 1;
    }

    PROTECT(t, map);
    PROTECT(t, table);

    for (unsigned i = 0; i < arrayLength(t, table); i += increment) {
      object interface = arrayBody(t, table, i);
      object name = className(t, interface);
      hashMapInsertOrReplace(t, map, name, interface, byteArrayHash,
                             byteArrayEqual);
    }
  }
}

void
parseInterfaceTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);
  
  object map = makeHashMap(t, 0, 0);
  PROTECT(t, map);

  if (classSuper(t, class_)) {
    addInterfaces(t, classSuper(t, class_), map);
  }
  
  unsigned count = s.read2();
  for (unsigned i = 0; i < count; ++i) {
    object name = arrayBody(t, pool, s.read2() - 1);
    PROTECT(t, name);

    object interface = resolveClass(t, name);
    PROTECT(t, interface);

    hashMapInsertOrReplace(t, map, name, interface, byteArrayHash,
                           byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  object interfaceTable = 0;
  if (hashMapSize(t, map)) {
    unsigned length = hashMapSize(t, map) ;
    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = makeArray(t, length, true);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    object it = hashMapIterator(t, map);
    PROTECT(t, it);

    for (; it; it = hashMapIteratorNext(t, it)) {
      object interface = resolveClass
        (t, tripleFirst(t, hashMapIteratorNode(t, it)));
      if (UNLIKELY(t->exception)) return;

      set(t, arrayBody(t, interfaceTable, i++), interface);

      if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
        // we'll fill in this table in parseMethodTable():
        object vtable = makeArray
          (t, arrayLength(t, classVirtualTable(t, interface)), true);
        
        set(t, arrayBody(t, interfaceTable, i++), vtable);
      }
    }
  }

  set(t, classInterfaceTable(t, class_), interfaceTable);
}

inline unsigned
fieldSize(Thread* t, object field)
{
  unsigned code = fieldCode(t, field);
  if (code == ObjectField) {
    return BytesPerWord;
  } else {
    return primitiveSize(t, code);
  }
}

void
parseFieldTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  unsigned memberOffset = BytesPerWord;
  if (classSuper(t, class_)) {
    memberOffset = classFixedSize(t, classSuper(t, class_));
  }

  unsigned count = s.read2();
  if (count) {
    unsigned staticOffset = 0;
  
    object fieldTable = makeArray(t, count, true);
    PROTECT(t, fieldTable);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        s.read2();
        s.skip(s.read4());
      }

      object field = makeField
        (t,
         flags,
         0, // offset
         fieldCode(t, byteArrayBody(t, arrayBody(t, pool, spec - 1), 0)),
         arrayBody(t, pool, name - 1),
         arrayBody(t, pool, spec - 1),
         class_);

      if (flags & ACC_STATIC) {
        fieldOffset(t, field) = staticOffset++;
      } else {
        unsigned excess = memberOffset % BytesPerWord;
        if (excess and fieldCode(t, field) == ObjectField) {
          memberOffset += BytesPerWord - excess;
        }

        fieldOffset(t, field) = memberOffset;
        memberOffset += fieldSize(t, field);
      }

      set(t, arrayBody(t, fieldTable, i), field);
    }

    set(t, classFieldTable(t, class_), fieldTable);

    if (staticOffset) {
      object staticTable = makeArray(t, staticOffset, true);

      set(t, classStaticTable(t, class_), staticTable);
    }
  }

  classFixedSize(t, class_) = pad(memberOffset);
  
  object mask = makeIntArray
    (t, divide(classFixedSize(t, class_), BitsPerWord), true);
  intArrayBody(t, mask, 0) = 1;

  bool sawReferenceField = false;
  for (object c = class_; c; c = classSuper(t, c)) {
    object fieldTable = classFieldTable(t, c);
    if (fieldTable) {
      for (int i = arrayLength(t, fieldTable) - 1; i >= 0; --i) {
        object field = arrayBody(t, fieldTable, i);
        if (fieldCode(t, field) == ObjectField) {
          unsigned index = fieldOffset(t, field) / BytesPerWord;
          intArrayBody(t, mask, (index / 32)) |= 1 << (index % 32);
          sawReferenceField = true;
        }
      }
    }
  }

  if (sawReferenceField) {
    set(t, classObjectMask(t, class_), mask);
  }
}

object
parseCode(Thread* t, Stream& s, object pool)
{
  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  object code = makeCode(t, pool, 0, 0, maxStack, maxLocals, length, false);
  s.read(&codeBody(t, code, 0), length);

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    PROTECT(t, code);

    object eht = makeExceptionHandlerTable(t, ehtLength, false);
    for (unsigned i = 0; i < ehtLength; ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
      exceptionHandlerStart(eh) = s.read2();
      exceptionHandlerEnd(eh) = s.read2();
      exceptionHandlerIp(eh) = s.read2();
      exceptionHandlerCatchType(eh) = s.read2();
    }

    set(t, codeExceptionHandlerTable(t, code), eht);
  }

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    object name = arrayBody(t, pool, s.read2() - 1);
    unsigned length = s.read4();

    if (strcmp(reinterpret_cast<const int8_t*>("LineNumberTable"),
               &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned lntLength = s.read2();
      object lnt = makeLineNumberTable(t, lntLength, false);
      for (unsigned i = 0; i < lntLength; ++i) {
        LineNumber* ln = lineNumberTableBody(t, lnt, i);
        lineNumberIp(ln) = s.read2();
        lineNumberLine(ln) = s.read2();
      }

      set(t, codeLineNumberTable(t, code), lnt);
    } else {
      s.skip(length);
    }
  }

  return code;
}

unsigned
parameterFootprint(Thread* t, object spec)
{
  unsigned footprint = 0;
  const char* s = reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    switch (*s) {
    case 'L':
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      while (*s == '[') ++ s;
      break;
      
    case 'J':
    case 'D':
      ++ s;
      ++ footprint;
      break;

    default:
      ++ s;
      break;
    }

    ++ footprint;
  }

  return footprint;
}

unsigned
parameterCount(Thread* t, object spec)
{
  unsigned count = 0;
  const char* s = reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    switch (*s) {
    case 'L':
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      while (*s == '[') ++ s;
      break;
      
    default:
      ++ s;
      break;
    }

    ++ count;
  }

  return count;
}

int
lineNumber(Thread* t, object method, unsigned ip)
{
  if (methodFlags(t, method) & ACC_NATIVE) {
    return NativeLine;
  }

  object table = codeLineNumberTable(t, methodCode(t, method));
  if (table) {
    // todo: do a binary search:
    int last = UnknownLine;
    for (unsigned i = 0; i < lineNumberTableLength(t, table); ++i) {
      if (ip <= lineNumberIp(lineNumberTableBody(t, table, i))) {
        return last;
      } else {
        last = lineNumberLine(lineNumberTableBody(t, table, i));
      }
    }
    return last;
  } else {
    return UnknownLine;
  }
}

unsigned
mangledSize(int8_t c)
{
  switch (c) {
  case '_':
  case ';':
  case '[':
    return 2;

  case '$':
    return 6;

  default:
    return 1;
  }
}

unsigned
mangle(int8_t c, int8_t* dst)
{
  switch (c) {
  case '/':
    dst[0] = '_';
    return 1;

  case '_':
    dst[0] = '_';
    dst[1] = '1';
    return 2;

  case ';':
    dst[0] = '_';
    dst[1] = '2';
    return 2;

  case '[':
    dst[0] = '_';
    dst[1] = '3';
    return 2;

  case '$':
    memcpy(dst, "_00024", 6);
    return 6;

  default:
    dst[0] = c;    
    return 1;
  }
}

object
makeJNIName(Thread* t, object method, bool decorate)
{
  unsigned size = 5;
  object className = ::className(t, methodClass(t, method));
  PROTECT(t, className);
  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, className, i));
  }

  ++ size;

  object methodName = ::methodName(t, method);
  PROTECT(t, methodName);
  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    size += mangledSize(byteArrayBody(t, methodName, i));
  }

  object methodSpec = ::methodSpec(t, method);
  PROTECT(t, methodSpec);
  if (decorate) {
    size += 2;
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      size += mangledSize(byteArrayBody(t, methodSpec, i));
    }
  }

  object name = makeByteArray(t, size + 1, false);
  unsigned index = 0;

  memcpy(&byteArrayBody(t, name, index), "Java_", 5);
  index += 5;

  for (unsigned i = 0; i < byteArrayLength(t, className) - 1; ++i) {
    index += mangle(byteArrayBody(t, className, i),
                    &byteArrayBody(t, name, index));
  }

  byteArrayBody(t, name, index++) = '_';

  for (unsigned i = 0; i < byteArrayLength(t, methodName) - 1; ++i) {
    index += mangle(byteArrayBody(t, methodName, i),
                    &byteArrayBody(t, name, index));
  }
  
  if (decorate) {
    byteArrayBody(t, name, index++) = '_';
    byteArrayBody(t, name, index++) = '_';
    for (unsigned i = 1; i < byteArrayLength(t, methodSpec) - 1
           and byteArrayBody(t, methodSpec, i) != ')'; ++i)
    {
      index += mangle(byteArrayBody(t, className, i),
                      &byteArrayBody(t, name, index));
    }
  }

  byteArrayBody(t, name, index++) = 0;

  assert(t, index == size + 1);

  return name;
}

void
parseMethodTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  object virtualMap = makeHashMap(t, 0, 0);
  PROTECT(t, virtualMap);

  object nativeMap = makeHashMap(t, 0, 0);
  PROTECT(t, nativeMap);

  unsigned virtualCount = 0;

  object superVirtualTable = 0;
  PROTECT(t, superVirtualTable);

  if (classFlags(t, class_) & ACC_INTERFACE) {
    object itable = classInterfaceTable(t, class_);
    if (itable) {
      for (unsigned i = 0; i < arrayLength(t, itable); ++i) {
        object vtable = classVirtualTable(t, arrayBody(t, itable, i));
        for (unsigned j = 0; j < virtualCount; ++j) {
          object method = arrayBody(t, vtable, j);
          if (hashMapInsertOrReplace(t, virtualMap, method, method, methodHash,
                                     methodEqual))
          {
            ++ virtualCount;
          }
        }
      }
    }
  } else {
    if (classSuper(t, class_)) {
      superVirtualTable = classVirtualTable(t, classSuper(t, class_));
    }

    if (superVirtualTable) {
      virtualCount = arrayLength(t, superVirtualTable);
      for (unsigned i = 0; i < virtualCount; ++i) {
        object method = arrayBody(t, superVirtualTable, i);
        hashMapInsert(t, virtualMap, method, method, methodHash);
      }
    }
  }

  object newVirtuals = makeList(t, 0, 0, 0);
  PROTECT(t, newVirtuals);
  
  unsigned count = s.read2();
  if (count) {
    object methodTable = makeArray(t, count, true);
    PROTECT(t, methodTable);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      object code = 0;
      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object name = arrayBody(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (strcmp(reinterpret_cast<const int8_t*>("Code"),
                   &byteArrayBody(t, name, 0)) == 0)
        {
          code = parseCode(t, s, pool);
        } else {
          s.skip(length);
        }
      }

      unsigned parameterCount = ::parameterCount
        (t, arrayBody(t, pool, spec - 1));

      unsigned parameterFootprint = ::parameterFootprint
        (t, arrayBody(t, pool, spec - 1));

      if ((flags & ACC_STATIC) == 0) {
        ++ parameterCount;
        ++ parameterFootprint;
      }

      object method = makeMethod(t,
                                 flags,
                                 0, // offset
                                 parameterCount,
                                 parameterFootprint,
                                 arrayBody(t, pool, name - 1),
                                 arrayBody(t, pool, spec - 1),
                                 class_,
                                 code);
      PROTECT(t, method);

      if (flags & ACC_STATIC) {
        if (strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                   &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          set(t, classInitializer(t, class_), method);
        }
      } else {
        object p = hashMapFindNode
          (t, virtualMap, method, methodHash, methodEqual);

        if (p) {
          methodOffset(t, method) = methodOffset(t, tripleFirst(t, p));

          set(t, tripleSecond(t, p), method);
        } else {
          methodOffset(t, method) = virtualCount++;

          listAppend(t, newVirtuals, method);
        }
      }

      if (flags & ACC_NATIVE) {
        object p = hashMapFindNode
          (t, nativeMap, method, methodHash, methodEqual);

        if (p == 0) {
          hashMapInsert(t, virtualMap, method, method, methodHash);
        }
      }

      set(t, arrayBody(t, methodTable, i), method);
    }

    for (unsigned i = 0; i < count; ++i) {
      object method = arrayBody(t, methodTable, i);

      if (methodFlags(t, method) & ACC_NATIVE) {
        object p = hashMapFindNode
          (t, nativeMap, method, methodHash, methodEqual);

        object jniName = makeJNIName(t, method, p != 0);
        set(t, methodCode(t, method), jniName);
      }
    }

    set(t, classMethodTable(t, class_), methodTable);
  }

  if (virtualCount) {
    // generate class vtable

    unsigned i = 0;
    object vtable = makeArray(t, virtualCount, false);

    if (classFlags(t, class_) & ACC_INTERFACE) {
      object it = hashMapIterator(t, virtualMap);

      for (; it; it = hashMapIteratorNext(t, it)) {
        object method = tripleFirst(t, hashMapIteratorNode(t, it));
        set(t, arrayBody(t, vtable, i++), method);
      }
    } else {
      if (superVirtualTable) {
        for (; i < arrayLength(t, superVirtualTable); ++i) {
          object method = arrayBody(t, superVirtualTable, i);
          method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);

          set(t, arrayBody(t, vtable, i), method);
        }
      }
    }

    for (object p = listFront(t, newVirtuals); p; p = pairSecond(t, p)) {
      set(t, arrayBody(t, vtable, i++), pairFirst(t, p));        
    }

    set(t, classVirtualTable(t, class_), vtable);

    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      // generate interface vtables
    
      object itable = classInterfaceTable(t, class_);
      if (itable) {
        PROTECT(t, itable);

        for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
          object ivtable = classVirtualTable(t, arrayBody(t, itable, i));
          object vtable = arrayBody(t, itable, i + 1);
        
          for (unsigned j = 0; j < arrayLength(t, ivtable); ++j) {
            object method = arrayBody(t, ivtable, j);
            method = hashMapFind
              (t, virtualMap, method, methodHash, methodEqual);
          
            set(t, arrayBody(t, vtable, j), method);        
          }
        }
      }
    }
  }
}

object
parseClass(Thread* t, const uint8_t* data, unsigned size)
{
  class Client : public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void NO_RETURN handleEOS() {
      abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, data, size);

  uint32_t magic = s.read4();
  assert(t, magic == 0xCAFEBABE);
  s.read2(); // minor version
  s.read2(); // major version

  object pool = parsePool(t, s);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  object class_ = makeClass(t,
                            flags,
                            0, // VM flags
                            0, // fixed size
                            0, // array size
                            0, // object mask
                            arrayBody(t, pool, name - 1),
                            0, // super
                            0, // interfaces
                            0, // vtable
                            0, // fields
                            0, // methods
                            0, // static table
                            0); // initializer
  PROTECT(t, class_);
  
  unsigned super = s.read2();
  if (super) {
    object sc = resolveClass(t, arrayBody(t, pool, super - 1));
    if (UNLIKELY(t->exception)) return 0;

    set(t, classSuper(t, class_), sc);

    classVmFlags(t, class_) |= classVmFlags(t, sc);
  }
  
  parseInterfaceTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseFieldTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseMethodTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  return class_;
}

void
updateBootstrapClass(Thread* t, object bootstrapClass, object class_)
{
  expect(t, bootstrapClass != class_);

  // verify that the classes have the same layout
  expect(t, classSuper(t, bootstrapClass) == classSuper(t, class_));
  expect(t, classFixedSize(t, bootstrapClass) == classFixedSize(t, class_));
  expect(t, (classObjectMask(t, bootstrapClass) == 0
             and classObjectMask(t, class_) == 0)
         or intArrayEqual(t, classObjectMask(t, bootstrapClass),
                          classObjectMask(t, class_)));

  PROTECT(t, bootstrapClass);
  PROTECT(t, class_);

  ENTER(t, Thread::ExclusiveState);

  classVmFlags(t, class_) |= classVmFlags(t, bootstrapClass);

  memcpy(bootstrapClass,
         class_,
         extendedSize(t, class_, baseSize(t, class_, objectClass(t, class_)))
         * BytesPerWord);

  object fieldTable = classFieldTable(t, class_);
  if (fieldTable) {
    for (unsigned i = 0; i < arrayLength(t, fieldTable); ++i) {
      set(t, fieldClass(t, arrayBody(t, fieldTable, i)), bootstrapClass);
    }
  }

  object methodTable = classMethodTable(t, class_);
  if (methodTable) {
    for (unsigned i = 0; i < arrayLength(t, methodTable); ++i) {
      set(t, methodClass(t, arrayBody(t, methodTable, i)), bootstrapClass);
    }
  }
}

object
resolveClass(Thread* t, object spec)
{
  PROTECT(t, spec);
  ACQUIRE(t, t->vm->classLock);

  object class_ = hashMapFind
    (t, t->vm->classMap, spec, byteArrayHash, byteArrayEqual);
  if (class_ == 0) {
    ClassFinder::Data* data = t->vm->classFinder->find
      (reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0)));

    if (data) {
      if (Verbose) {
        fprintf(stderr, "parsing %s\n", &byteArrayBody
                (t, spec, 0));
      }

      // parse class file
      class_ = parseClass(t, data->start(), data->length());
      data->dispose();

      if (Verbose) {
        fprintf(stderr, "done parsing %s\n", &byteArrayBody
                (t, className(t, class_), 0));
      }

      PROTECT(t, class_);

      object bootstrapClass = hashMapFind
        (t, t->vm->bootstrapClassMap, spec, byteArrayHash, byteArrayEqual);

      if (bootstrapClass) {
        PROTECT(t, bootstrapClass);

        updateBootstrapClass(t, bootstrapClass, class_);
        class_ = bootstrapClass;
      }

      hashMapInsert(t, t->vm->classMap, spec, class_, byteArrayHash);
    } else {
      object message = makeString(t, "%s", &byteArrayBody(t, spec, 0));
      t->exception = makeClassNotFoundException(t, message);
    }
  }
  return class_;
}

inline object
resolveClass(Thread* t, object pool, unsigned index)
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    PROTECT(t, pool);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
  }
  return o; 
}

inline object
resolveClass(Thread* t, object container, object& (*class_)(Thread*, object))
{
  object o = class_(t, container);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    PROTECT(t, container);

    o = resolveClass(t, o);
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, class_(t, container), o);
  }
  return o; 
}

inline object
resolve(Thread* t, object pool, unsigned index,
        object (*find)(Thread*, object, object))
{
  object o = arrayBody(t, pool, index);
  if (objectClass(t, o) == arrayBody(t, t->vm->types, Machine::ReferenceType))
  {
    PROTECT(t, pool);

    object class_ = resolveClass(t, o, referenceClass);
    if (UNLIKELY(t->exception)) return 0;

    o = find(t, class_, arrayBody(t, pool, index));
    if (UNLIKELY(t->exception)) return 0;
    
    set(t, arrayBody(t, pool, index), o);
  }
  return o;
}

inline object
resolveField(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findFieldInClass);
}

inline object
resolveMethod(Thread* t, object pool, unsigned index)
{
  return resolve(t, pool, index, findMethodInClass);
}

object
makeNativeMethodData(Thread* t, object method, void* function, bool builtin)
{
  PROTECT(t, method);

  object data = makeNativeMethodData(t,
                                     function,
                                     0, // argument table size
                                     0, // return code,
                                     builtin,
                                     methodParameterCount(t, method) + 1,
                                     false);
        
  unsigned argumentTableSize = BytesPerWord;
  unsigned index = 0;

  nativeMethodDataParameterTypes(t, data, index++) = POINTER_TYPE;

  if ((methodFlags(t, method) & ACC_STATIC) == 0) {
    nativeMethodDataParameterTypes(t, data, index++) = POINTER_TYPE;
    argumentTableSize += BytesPerWord;
  }

  const char* s = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    unsigned code = fieldCode(t, *s);
    nativeMethodDataParameterTypes(t, data, index++) = fieldType(t, code);

    switch (*s) {
    case 'L':
      argumentTableSize += BytesPerWord;
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      argumentTableSize += BytesPerWord;
      while (*s == '[') ++ s;
      break;
      
    default:
      argumentTableSize += pad(primitiveSize(t, code));
      ++ s;
      break;
    }
  }

  nativeMethodDataArgumentTableSize(t, data) = argumentTableSize;
  nativeMethodDataReturnCode(t, data) = fieldCode(t, s[1]);

  return data;
}

inline object
resolveNativeMethodData(Thread* t, object method)
{
  if (objectClass(t, methodCode(t, method))
      == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    object data = 0;
    for (System::Library* lib = t->vm->libraries; lib; lib = lib->next()) {
      void* p = lib->resolve(reinterpret_cast<const char*>
                             (&byteArrayBody(t, methodCode(t, method), 0)));
      if (p) {
        PROTECT(t, method);
        data = makeNativeMethodData(t, method, p, false);
        break;
      }
    }

    if (data == 0) {
      object p = hashMapFind(t, t->vm->builtinMap, methodCode(t, method),
                             byteArrayHash, byteArrayEqual);
      if (p) {
        PROTECT(t, method);
        data = makeNativeMethodData(t, method, pointerValue(t, p), true);
      }
    }

    if (LIKELY(data)) {
      set(t, methodCode(t, method), data);
    } else {
      object message = makeString
        (t, "%s", &byteArrayBody(t, methodCode(t, method), 0));
      t->exception = makeUnsatisfiedLinkError(t, message);
    }

    return data;
  } else {
    return methodCode(t, method);
  }  
}

inline void
invokeNative(Thread* t, object method)
{
  object data = resolveNativeMethodData(t, method);
  if (UNLIKELY(t->exception)) {
    return;
  }

  unsigned footprint = methodParameterFootprint(t, method);
  unsigned count = methodParameterCount(t, method);

  unsigned size = nativeMethodDataArgumentTableSize(t, data);
  uintptr_t args[size / BytesPerWord];
  unsigned offset = 0;

  args[offset++] = reinterpret_cast<uintptr_t>(t);

  unsigned sp = t->sp - footprint;
  for (unsigned i = 0; i < count; ++i) {
    unsigned type = nativeMethodDataParameterTypes(t, data, i + 1);

    switch (type) {
    case INT8_TYPE:
    case INT16_TYPE:
    case INT32_TYPE:
    case FLOAT_TYPE:
      args[offset++] = peekInt(t, sp++);
      break;

    case INT64_TYPE:
    case DOUBLE_TYPE: {
      uint64_t v = peekLong(t, sp);
      memcpy(args + offset, &v, 8);
      offset += (8 / BytesPerWord);
      sp += 2;
    } break;

    case POINTER_TYPE:
      args[offset++] = reinterpret_cast<uintptr_t>
        (t->stack + ((sp++) * 2) + 1);
      break;

    default: abort(t);
    }
  }

  unsigned returnCode = nativeMethodDataReturnCode(t, data);
  unsigned returnType = fieldType(t, returnCode);
  void* function = nativeMethodDataFunction(t, data);

  bool builtin = nativeMethodDataBuiltin(t, data);
  if (not builtin) {
    enter(t, Thread::IdleState);
  }

  uint64_t result = t->vm->system->call
    (function,
     args,
     &nativeMethodDataParameterTypes(t, data, 0),
     count + 1,
     size,
     returnType);

  if (not builtin) {
    enter(t, Thread::ActiveState);
  }

  if (UNLIKELY(t->exception)) {
    return;
  }

  t->sp = frameStackBase(t, t->frame);

  switch (returnCode) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    pushInt(t, result);
    break;

  case LongField:
  case DoubleField:
    pushLong(t, result);
    break;

  case ObjectField:
    pushObject(t, result == 0 ? 0 :
               *reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
    break;

  case VoidField:
    break;

  default:
    abort(t);
  };
}

inline object
localObject(Thread* t, object frame, unsigned index)
{
  return peekObject(t, frameStackBase(t, frame) + index);
}

inline uint32_t
localInt(Thread* t, object frame, unsigned index)
{
  return peekInt(t, frameStackBase(t, frame) + index);
}

inline uint64_t
localLong(Thread* t, object frame, unsigned index)
{
  return peekLong(t, frameStackBase(t, frame) + index);
}

inline void
setLocalObject(Thread* t, object frame, unsigned index, object value)
{
  pokeObject(t, frameStackBase(t, frame) + index, value);
}

inline void
setLocalInt(Thread* t, object frame, unsigned index, uint32_t value)
{
  pokeInt(t, frameStackBase(t, frame) + index, value);
}

inline void
setLocalLong(Thread* t, object frame, unsigned index, uint64_t value)
{
  pokeLong(t, frameStackBase(t, frame) + index, value);
}

namespace builtin {

void
loadLibrary(JNIEnv* e, jstring nameString)
{
  Thread* t = static_cast<Thread*>(e);

  if (LIKELY(nameString)) {
    object n = *nameString;
    char name[stringLength(t, n) + 1];
    memcpy(name,
           &byteArrayBody(t, stringBytes(t, n), stringOffset(t, n)),
           stringLength(t, n));
    name[stringLength(t, n)] = 0;

    System::Library* lib;
    if (LIKELY(t->vm->system->success
               (t->vm->system->load(&lib, name, t->vm->libraries))))
    {
      t->vm->libraries = lib;
    } else {
      object message = makeString(t, "library not found: %s", name);
      t->exception = makeRuntimeException(t, message);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }
}

jstring
toString(JNIEnv* e, jobject this_)
{
  Thread* t = static_cast<Thread*>(e);

  object s = makeString
    (t, "%s@%p",
     &byteArrayBody(t, className(t, objectClass(t, *this_)), 0),
     *this_);

  return pushReference(t, s);
}

jarray
trace(JNIEnv* e, jint skipCount)
{
  Thread* t = static_cast<Thread*>(e);

  object frame = t->frame;
  while (skipCount--) frame = frameNext(t, frame);
  
  if (methodClass(t, frameMethod(t, frame))
      == arrayBody(t, t->vm->types, Machine::ThrowableType))
  {
    // skip Throwable constructors
    while (strcmp(reinterpret_cast<const int8_t*>("<init>"),
                  &byteArrayBody(t, methodName(t, frameMethod(t, frame)), 0))
           == 0)
    {
      frame = frameNext(t, frame);
    }
  }

  return pushReference(t, makeTrace(t, frame));
}

} // namespace builtin

namespace jni {

jsize
GetStringUTFLength(JNIEnv* e, jstring s)
{
  Thread* t = static_cast<Thread*>(e);

  ENTER(t, Thread::ActiveState);

  jsize length = 0;
  if (LIKELY(s)) {
    length = stringLength(t, *s);
  } else {
    t->exception = makeNullPointerException(t);
  }

  return length;
}

const char*
GetStringUTFChars(JNIEnv* e, jstring s, jboolean* isCopy)
{
  Thread* t = static_cast<Thread*>(e);

  ENTER(t, Thread::ActiveState);

  char* chars = 0;
  if (LIKELY(s)) {
    chars = static_cast<char*>
      (t->vm->system->allocate(stringLength(t, *s) + 1));

    memcpy(chars,
           &byteArrayBody(t, stringBytes(t, *s), stringOffset(t, *s)),
           stringLength(t, *s));

    chars[stringLength(t, *s)] = 0;
  } else {
    t->exception = makeNullPointerException(t);
  }

  if (isCopy) *isCopy = true;
  return chars;
}

void
ReleaseStringUTFChars(JNIEnv* e, jstring, const char* chars)
{
  static_cast<Thread*>(e)->vm->system->free(chars);
}

} // namespace jni

Machine::Machine(System* system, Heap* heap, ClassFinder* classFinder):
  system(system),
  heap(heap),
  classFinder(classFinder),
  rootThread(0),
  exclusive(0),
  activeCount(0),
  liveCount(0),
  stateLock(0),
  heapLock(0),
  classLock(0),
  finalizerLock(0),
  libraries(0),
  classMap(0),
  bootstrapClassMap(0),
  builtinMap(0),
  monitorMap(0),
  types(0),
  finalizers(0),
  doomed(0),
  weakReferences(0),
  unsafe(false)
{
  memset(&jniEnvVTable, 0, sizeof(JNIEnvVTable));

  jniEnvVTable.GetStringUTFLength = jni::GetStringUTFLength;
  jniEnvVTable.GetStringUTFChars = jni::GetStringUTFChars;
  jniEnvVTable.ReleaseStringUTFChars = jni::ReleaseStringUTFChars;

  if (not system->success(system->make(&stateLock)) or
      not system->success(system->make(&heapLock)) or
      not system->success(system->make(&classLock)) or
      not system->success(system->make(&finalizerLock)))
  {
    system->abort();
  }
}

void
Machine::dispose()
{
  stateLock->dispose();
  heapLock->dispose();
  classLock->dispose();
  finalizerLock->dispose();

  if (libraries) {
    libraries->dispose();
  }
  
  if (rootThread) {
    rootThread->dispose();
  }
}

Thread::Thread(Machine* m):
  JNIEnv(&(m->jniEnvVTable)),
  vm(m),
  next(0),
  child(0),
  state(NoState),
  thread(0),
  frame(0),
  code(0),
  exception(0),
  ip(0),
  sp(0),
  heapIndex(0),
  protector(0),
  chain(0)
{
  if (m->rootThread == 0) {
    m->rootThread = this;
    m->unsafe = true;

    Thread* t = this;

#include "type-initializations.cpp"

    object arrayClass = arrayBody(t, t->vm->types, Machine::ArrayType);
    set(t, cast<object>(t->vm->types, 0), arrayClass);

    object classClass = arrayBody(t, m->types, Machine::ClassType);
    set(t, cast<object>(classClass, 0), classClass);

    object intArrayClass = arrayBody(t, m->types, Machine::IntArrayType);
    set(t, cast<object>(intArrayClass, 0), classClass);

    m->unsafe = false;

    m->bootstrapClassMap = makeHashMap(this, 0, 0);

#include "type-java-initializations.cpp"

    classVmFlags(t, arrayBody(t, m->types, Machine::WeakReferenceType))
      |= WeakReferenceFlag;

    m->classMap = makeHashMap(this, 0, 0);
    m->builtinMap = makeHashMap(this, 0, 0);
    m->monitorMap = makeHashMap(this, 0, 0);

    struct {
      const char* key;
      void* value;
    } builtins[] = {
      { "Java_java_lang_Object_toString",
        reinterpret_cast<void*>(builtin::toString) },
      { "Java_java_lang_System_loadLibrary",
        reinterpret_cast<void*>(builtin::loadLibrary) },
      { "Java_java_lang_Throwable_trace",
        reinterpret_cast<void*>(builtin::trace) },
      { 0, 0 }
    };

    for (unsigned i = 0; builtins[i].key; ++i) {
      object key = makeByteArray(t, builtins[i].key);
      PROTECT(t, key);
      object value = makePointer(t, builtins[i].value);

      hashMapInsert(t, m->builtinMap, key, value, byteArrayHash);
    }
  }
}

object
run(Thread* t)
{
  unsigned instruction = nop;
  unsigned& ip = t->ip;
  unsigned& sp = t->sp;
  object& code = t->code;
  object& frame = t->frame;
  object& exception = t->exception;
  uintptr_t* stack = t->stack;

  if (UNLIKELY(exception)) goto throw_;

 loop:
  instruction = codeBody(t, code, ip++);

  if (DebugRun) {
    fprintf(stderr, "ip: %d; instruction: 0x%x in %s.%s ",
            ip - 1,
            instruction,
            &byteArrayBody
            (t, className(t, methodClass(t, frameMethod(t, frame))), 0),
            &byteArrayBody
            (t, methodName(t, frameMethod(t, frame)), 0));

    int line = lineNumber(t, frameMethod(t, frame), ip);
    switch (line) {
    case NativeLine:
      fprintf(stderr, "(native)\n");
      break;
    case UnknownLine:
      fprintf(stderr, "(unknown line)\n");
      break;
    default:
      fprintf(stderr, "(line %d)\n", line);
    }
  }

  switch (instruction) {
  case aaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < objectArrayLength(t, array)))
      {
        pushObject(t, objectArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    objectArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case aastore: {
    object value = popObject(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < objectArrayLength(t, array)))
      {
        set(t, objectArrayBody(t, array, index), value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    objectArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case aconst_null: {
    pushObject(t, 0);
  } goto loop;

  case aload: {
    pushObject(t, localObject(t, frame, codeBody(t, code, ip++)));
  } goto loop;

  case aload_0: {
    pushObject(t, localObject(t, frame, 0));
  } goto loop;

  case aload_1: {
    pushObject(t, localObject(t, frame, 1));
  } goto loop;

  case aload_2: {
    pushObject(t, localObject(t, frame, 2));
  } goto loop;

  case aload_3: {
    pushObject(t, localObject(t, frame, 3));
  } goto loop;

  case anewarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      object array = makeObjectArray(t, class_, count, true);
      
      pushObject(t, array);
    } else {
      object message = makeString(t, "%d", count);
      exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case areturn:
  case ireturn:
  case lreturn: {
    frame = frameNext(t, frame);
    if (frame) {
      code = methodCode(t, frameMethod(t, frame));
      ip = frameIp(t, frame);
      goto loop;
    } else {
      code = 0;
      switch (instruction) {
      case areturn:
        return popObject(t);

      case ireturn:
        return makeInt(t, popInt(t));

      case lreturn:
        return makeLong(t, popLong(t));
      }
    }
  } goto loop;

  case arraylength: {
    object array = popObject(t);
    if (LIKELY(array)) {
      if (objectClass(t, array)
          == arrayBody(t, t->vm->types, Machine::ObjectArrayType))
      {
        pushInt(t, objectArrayLength(t, array));
      } else {
        // for all other array types, the length follow the class pointer.
        pushInt(t, cast<uint32_t>(array, BytesPerWord));
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } abort(t);

  case astore: {
    setLocalObject(t, frame, codeBody(t, code, ip++), popObject(t));
  } goto loop;

  case astore_0: {
    setLocalObject(t, frame, 0, popObject(t));
  } goto loop;

  case astore_1: {
    setLocalObject(t, frame, 1, popObject(t));
  } goto loop;

  case astore_2: {
    setLocalObject(t, frame, 2, popObject(t));
  } goto loop;

  case astore_3: {
    setLocalObject(t, frame, 3, popObject(t));
  } goto loop;

  case athrow: {
    exception = popObject(t);
    if (UNLIKELY(exception == 0)) {
      exception = makeNullPointerException(t);      
    }
  } goto throw_;

  case baload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < byteArrayLength(t, array)))
      {
        pushInt(t, byteArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    byteArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case bastore: {
    int8_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < byteArrayLength(t, array)))
      {
        byteArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    byteArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case bipush: {
    pushInt(t, codeBody(t, code, ip++));
  } goto loop;

  case caload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < charArrayLength(t, array)))
      {
        pushInt(t, charArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    charArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case castore: {
    uint16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < charArrayLength(t, array)))
      {
        charArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    charArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case checkcast: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    if (peekObject(t, sp - 1)) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (not instanceOf(t, class_, peekObject(t, sp - 1))) {
        object message = makeString
          (t, "%s as %s",
           &byteArrayBody
           (t, className(t, objectClass(t, peekObject(t, sp - 1))), 0),
           &byteArrayBody(t, className(t, class_), 0));
        exception = makeClassCastException(t, message);
        goto throw_;
      }
    }
  } goto loop;

  case dup: {
    if (DebugStack) {
      fprintf(stderr, "dup\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup_x1\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp    ) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup_x2\n");
    }

    memcpy(stack + ((sp    ) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp    ) * 2), BytesPerWord * 2);
    ++ sp;
  } goto loop;

  case dup2: {
    if (DebugStack) {
      fprintf(stderr, "dup2\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case dup2_x1: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x1\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp    ) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 3) * 2), stack + ((sp    ) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case dup2_x2: {
    if (DebugStack) {
      fprintf(stderr, "dup2_x2\n");
    }

    memcpy(stack + ((sp + 1) * 2), stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp    ) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 3) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), stack + ((sp - 4) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 4) * 2), stack + ((sp    ) * 2), BytesPerWord * 4);
    sp += 2;
  } goto loop;

  case getfield: {
    object instance = popObject(t);

    if (LIKELY(instance)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      switch (fieldCode(t, field)) {
      case ByteField:
      case BooleanField:
        pushInt(t, cast<int8_t>(instance, fieldOffset(t, field)));

      case CharField:
      case ShortField:
        pushInt(t, cast<int16_t>(instance, fieldOffset(t, field)));

      case FloatField:
      case IntField:
        pushInt(t, cast<int32_t>(instance, fieldOffset(t, field)));

      case DoubleField:
      case LongField:
        pushLong(t, cast<int64_t>(instance, fieldOffset(t, field)));

      case ObjectField:
        pushObject(t, cast<object>(instance, fieldOffset(t, field)));

      default: abort(t);
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case getstatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, fieldClass(t, field));
    if (clinit) {
      set(t, classInitializer(t, fieldClass(t, field)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    object v = arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                         fieldOffset(t, field));

    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField:
      pushInt(t, intValue(t, v));
      break;

    case DoubleField:
    case LongField:
      pushLong(t, longValue(t, v));
      break;

    case ObjectField:
      pushObject(t, v);
      break;

    default: abort(t);
    }
  } goto loop;

  case goto_: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
  } goto loop;
    
  case goto_w: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);
    uint8_t offset3 = codeBody(t, code, ip++);
    uint8_t offset4 = codeBody(t, code, ip++);

    ip = (ip - 5) + static_cast<int32_t>
      (((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4));
  } goto loop;

  case i2b: {
    pushInt(t, static_cast<int8_t>(popInt(t)));
  } goto loop;

  case i2c: {
    pushInt(t, static_cast<uint16_t>(popInt(t)));
  } goto loop;

  case i2l: {
    pushLong(t, popInt(t));
  } goto loop;

  case i2s: {
    pushInt(t, static_cast<int16_t>(popInt(t)));
  } goto loop;

  case iadd: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a + b);
  } goto loop;

  case iaload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < intArrayLength(t, array)))
      {
        pushInt(t, intArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    intArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case iand: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a & b);
  } goto loop;

  case iastore: {
    int32_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < intArrayLength(t, array)))
      {
        intArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    intArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case iconst_0: {
    pushInt(t, 0);
  } goto loop;

  case iconst_1: {
    pushInt(t, 1);
  } goto loop;

  case iconst_2: {
    pushInt(t, 2);
  } goto loop;

  case iconst_3: {
    pushInt(t, 3);
  } goto loop;

  case iconst_4: {
    pushInt(t, 4);
  } goto loop;

  case iconst_5: {
    pushInt(t, 5);
  } goto loop;

  case idiv: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a / b);
  } goto loop;

  case if_acmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a == b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_acmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a != b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a == b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a != b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a > b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a >= b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmplt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a < b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmple: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    if (a < b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popInt(t) == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popInt(t)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) > 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) >= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iflt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) < 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifle: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (static_cast<int32_t>(popInt(t)) <= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnonnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popObject(t)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    if (popObject(t) == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iinc: {
    uint8_t index = codeBody(t, code, ip++);
    int8_t c = codeBody(t, code, ip++);
    
    setLocalInt(t, frame, index, localInt(t, frame, index) + c);
  } goto loop;

  case iload: {
    pushInt(t, localInt(t, frame, codeBody(t, code, ip++)));
  } goto loop;

  case iload_0: {
    pushInt(t, localInt(t, frame, 0));
  } goto loop;

  case iload_1: {
    pushInt(t, localInt(t, frame, 1));
  } goto loop;

  case iload_2: {
    pushInt(t, localInt(t, frame, 2));
  } goto loop;

  case iload_3: {
    pushInt(t, localInt(t, frame, 3));
  } goto loop;

  case imul: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a * b);
  } goto loop;

  case ineg: {
    pushInt(t, - popInt(t));
  } goto loop;

  case instanceof: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    if (peekObject(t, sp - 1)) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (instanceOf(t, class_, peekObject(t, sp - 1))) {
        pushInt(t, 1);
      } else {
        pushInt(t, 0);
      }
    } else {
      pushInt(t, 0);
    }
  } goto loop;

  case invokeinterface: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    ip += 2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      code = findInterfaceMethod
        (t, method, peekObject(t, sp - parameterFootprint));
      if (UNLIKELY(exception)) goto throw_;

      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case invokespecial: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      object class_ = methodClass(t, frameMethod(t, t->frame));
      if (isSpecialMethod(t, method, class_)) {
        code = findMethod(t, method, classSuper(t, class_));
        if (UNLIKELY(exception)) goto throw_;
      } else {
        code = method;
      }
      
      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case invokestatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    object clinit = classInitializer(t, methodClass(t, method));
    if (clinit) {
      set(t, classInitializer(t, methodClass(t, method)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    code = method;
  } goto invoke;

  case invokevirtual: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterFootprint = methodParameterFootprint(t, method);
    if (LIKELY(peekObject(t, sp - parameterFootprint))) {
      code = findVirtualMethod
        (t, method, peekObject(t, sp - parameterFootprint));
      if (UNLIKELY(exception)) goto throw_;
      
      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case ior: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a | b);
  } goto loop;

  case irem: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a % b);
  } goto loop;

  case ishl: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a << b);
  } goto loop;

  case ishr: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a >> b);
  } goto loop;

  case istore: {
    setLocalInt(t, frame, codeBody(t, code, ip++), popInt(t));
  } goto loop;

  case istore_0: {
    setLocalInt(t, frame, 0, popInt(t));
  } goto loop;

  case istore_1: {
    setLocalInt(t, frame, 1, popInt(t));
  } goto loop;

  case istore_2: {
    setLocalInt(t, frame, 2, popInt(t));
  } goto loop;

  case istore_3: {
    setLocalInt(t, frame, 3, popInt(t));
  } goto loop;

  case isub: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a - b);
  } goto loop;

  case iushr: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, static_cast<uint32_t>(a >> b));
  } goto loop;

  case ixor: {
    int32_t b = popInt(t);
    int32_t a = popInt(t);
    
    pushInt(t, a ^ b);
  } goto loop;

  case jsr: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    pushInt(t, ip);
    ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
  } goto loop;

  case jsr_w: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);
    uint8_t offset3 = codeBody(t, code, ip++);
    uint8_t offset4 = codeBody(t, code, ip++);

    pushInt(t, ip);
    ip = (ip - 3) + static_cast<int32_t>
      ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case l2i: {
    pushLong(t, static_cast<int32_t>(popLong(t)));
  } goto loop;

  case ladd: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a + b);
  } goto loop;

  case laload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < longArrayLength(t, array)))
      {
        pushLong(t, longArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    longArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case land: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a & b);
  } goto loop;

  case lastore: {
    int64_t value = popLong(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < longArrayLength(t, array)))
      {
        longArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    longArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case lcmp: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushInt(t, a > b ? 1 : a == b ? 0 : -1);
  } goto loop;

  case lconst_0: {
    pushLong(t, 0);
  } goto loop;

  case lconst_1: {
    pushLong(t, 1);
  } goto loop;

  case ldc:
  case ldc_w: {
    uint16_t index;

    if (instruction == ldc) {
      index = codeBody(t, code, ip++);
    } else {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      index = (index1 << 8) | index2;
    }

    object v = arrayBody(t, codePool(t, code), index - 1);

    if (objectClass(t, v) == arrayBody(t, t->vm->types, Machine::IntType)) {
      pushInt(t, intValue(t, v));
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::StringType))
    {
      pushObject(t, v);
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::FloatType))
    {
      pushInt(t, floatValue(t, v));
    }
  } goto loop;

  case ldc2_w: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    object v = arrayBody(t, codePool(t, code), ((index1 << 8) | index2) - 1);

    if (objectClass(t, v) == arrayBody(t, t->vm->types, Machine::LongType)) {
      pushLong(t, longValue(t, v));
    } else if (objectClass(t, v)
               == arrayBody(t, t->vm->types, Machine::DoubleType))
    {
      pushLong(t, doubleValue(t, v));
    }
  } goto loop;

  case vm::ldiv: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a / b);
  } goto loop;

  case lload: {
    pushLong(t, localLong(t, frame, codeBody(t, code, ip++)));
  } goto loop;

  case lload_0: {
    pushLong(t, localLong(t, frame, 0));
  } goto loop;

  case lload_1: {
    pushLong(t, localLong(t, frame, 1));
  } goto loop;

  case lload_2: {
    pushLong(t, localLong(t, frame, 2));
  } goto loop;

  case lload_3: {
    pushLong(t, localLong(t, frame, 3));
  } goto loop;

  case lmul: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a * b);
  } goto loop;

  case lneg: {
    pushLong(t, - popInt(t));
  } goto loop;

  case lor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a | b);
  } goto loop;

  case lrem: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a % b);
  } goto loop;

  case lshl: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a << b);
  } goto loop;

  case lshr: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a >> b);
  } goto loop;

  case lstore: {
    setLocalLong(t, frame, codeBody(t, code, ip++), popLong(t));
  } goto loop;

  case lstore_0: {
    setLocalLong(t, frame, 0, popLong(t));
  } goto loop;

  case lstore_1: {
    setLocalLong(t, frame, 1, popLong(t));
  } goto loop;

  case lstore_2: {
    setLocalLong(t, frame, 2, popLong(t));
  } goto loop;

  case lstore_3: {
    setLocalLong(t, frame, 3, popLong(t));
  } goto loop;

  case lsub: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a - b);
  } goto loop;

  case lushr: {
    uint64_t b = popLong(t);
    uint64_t a = popLong(t);
    
    pushLong(t, a >> b);
  } goto loop;

  case lxor: {
    int64_t b = popLong(t);
    int64_t a = popLong(t);
    
    pushLong(t, a ^ b);
  } goto loop;

  case monitorenter: {
    object o = popObject(t);
    if (LIKELY(o)) {
      objectMonitor(t, o)->acquire(t);
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case monitorexit: {
    object o = popObject(t);
    if (LIKELY(o)) {
      objectMonitor(t, o)->release(t);
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case new_: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    object class_ = resolveClass(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, class_);
    if (clinit) {
      set(t, classInitializer(t, class_), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    pushObject(t, make(t, class_));
  } goto loop;

  case newarray: {
    int32_t count = popInt(t);

    if (LIKELY(count >= 0)) {
      uint8_t type = codeBody(t, code, ip++);

      object array;

      switch (type) {
      case T_BOOLEAN:
        array = makeBooleanArray(t, count, true);
        break;

      case T_CHAR:
        array = makeCharArray(t, count, true);
        break;

      case T_FLOAT:
        array = makeFloatArray(t, count, true);
        break;

      case T_DOUBLE:
        array = makeDoubleArray(t, count, true);
        break;

      case T_BYTE:
        array = makeByteArray(t, count, true);
        break;

      case T_SHORT:
        array = makeShortArray(t, count, true);
        break;

      case T_INT:
        array = makeIntArray(t, count, true);
        break;

      case T_LONG:
        array = makeLongArray(t, count, true);
        break;

      default: abort(t);
      }
            
      pushObject(t, array);
    } else {
      object message = makeString(t, "%d", count);
      exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case nop: goto loop;

  case pop_: {
    -- sp;
  } goto loop;

  case pop2: {
    sp -= 2;
  } goto loop;

  case putfield: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
    
    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
      
    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField: {
      int32_t value = popInt(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        switch (fieldCode(t, field)) {
        case ByteField:
        case BooleanField:
          cast<int8_t>(o, fieldOffset(t, field)) = value;
          break;
            
        case CharField:
        case ShortField:
          cast<int16_t>(o, fieldOffset(t, field)) = value;
          break;
            
        case FloatField:
        case IntField:
          cast<int32_t>(o, fieldOffset(t, field)) = value;
          break;
        }
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    case DoubleField:
    case LongField: {
      int64_t value = popLong(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        cast<int64_t>(o, fieldOffset(t, field)) = value;
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    case ObjectField: {
      object value = popObject(t);
      object o = popObject(t);
      if (LIKELY(o)) {
        set(t, cast<object>(o, fieldOffset(t, field)), value);
      } else {
        exception = makeNullPointerException(t);
        goto throw_;
      }
    } break;

    default: abort(t);
    }
  } goto loop;

  case putstatic: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    object field = resolveField(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;

    object clinit = classInitializer(t, fieldClass(t, field));
    if (clinit) {
      set(t, classInitializer(t, fieldClass(t, field)), 0);
      code = clinit;
      ip -= 3;
      goto invoke;
    }

    PROTECT(t, field);
      
    object v;

    switch (fieldCode(t, field)) {
    case ByteField:
    case BooleanField:
    case CharField:
    case ShortField:
    case FloatField:
    case IntField: {
      v = makeInt(t, popInt(t));
    } break;

    case DoubleField:
    case LongField: {
      v = makeLong(t, popLong(t));
    } break;

    case ObjectField:
      v = popObject(t);
      break;

    default: abort(t);
    }

    set(t, arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                     fieldOffset(t, field)), v);
  } goto loop;

  case ret: {
    ip = localInt(t, frame, codeBody(t, code, ip));
  } goto loop;

  case return_: {
    frame = frameNext(t, frame);
    if (frame) {
      code = methodCode(t, frameMethod(t, frame));
      ip = frameIp(t, frame);
      goto loop;
    } else {
      code = 0;
      return 0;
    }
  } goto loop;

  case saload: {
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < shortArrayLength(t, array)))
      {
        pushInt(t, shortArrayBody(t, array, index));
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    shortArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case sastore: {
    int16_t value = popInt(t);
    int32_t index = popInt(t);
    object array = popObject(t);

    if (LIKELY(array)) {
      if (LIKELY(index >= 0 and
                 static_cast<uint32_t>(index) < shortArrayLength(t, array)))
      {
        shortArrayBody(t, array, index) = value;
      } else {
        object message = makeString(t, "%d not in [0,%d]", index,
                                    shortArrayLength(t, array));
        exception = makeArrayIndexOutOfBoundsException(t, message);
        goto throw_;
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case sipush: {
    uint8_t byte1 = codeBody(t, code, ip++);
    uint8_t byte2 = codeBody(t, code, ip++);

    pushInt(t, (byte1 << 8) | byte2);
  } goto loop;

  case swap: {
    uintptr_t tmp[2];
    memcpy(tmp                   , stack + ((sp - 1) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 1) * 2), stack + ((sp - 2) * 2), BytesPerWord * 2);
    memcpy(stack + ((sp - 2) * 2), tmp                   , BytesPerWord * 2);
  } goto loop;

  case wide: goto wide;

  default: abort(t);
  }

 wide:
  switch (codeBody(t, code, ip++)) {
  case aload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushObject(t, localObject(t, frame, (index1 << 8) | index2));
  } goto loop;

  case astore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalObject(t, frame, (index1 << 8) | index2, popObject(t));
  } goto loop;

  case iinc: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    uint8_t count1 = codeBody(t, code, ip++);
    uint8_t count2 = codeBody(t, code, ip++);
    uint16_t count = (count1 << 8) | count2;
    
    setLocalInt(t, frame, index, localInt(t, frame, index) + count);
  } goto loop;

  case iload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushInt(t, localInt(t, frame, (index1 << 8) | index2));
  } goto loop;

  case istore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalInt(t, frame, (index1 << 8) | index2, popInt(t));
  } goto loop;

  case lload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    pushLong(t, localLong(t, frame, (index1 << 8) | index2));
  } goto loop;

  case lstore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    setLocalLong(t, frame, (index1 << 8) | index2,  popLong(t));
  } goto loop;

  case ret: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    ip = localInt(t, frame, (index1 << 8) | index2);
  } goto loop;

  default: abort(t);
  }

 invoke: {    
    unsigned parameterFootprint = methodParameterFootprint(t, code);
    unsigned base = sp - parameterFootprint;

    if (methodFlags(t, code) & ACC_NATIVE) {
      frame = makeFrame(t, code, frame, 0, base);

      invokeNative(t, code);

      frame = frameNext(t, frame);

      if (UNLIKELY(exception)) {
        goto throw_;
      }

      code = methodCode(t, frameMethod(t, frame));
    } else {
      if (UNLIKELY(codeMaxStack(t, methodCode(t, code))
                   + codeMaxLocals(t, methodCode(t, code)) + base
                   > Thread::StackSizeInWords / 2))
      {
        exception = makeStackOverflowError(t);
        goto throw_;      
      }

      frameIp(t, frame) = ip;
      ip = 0;

      frame = makeFrame(t, code, frame, 0, base);
      code = methodCode(t, code);

      memset(stack + ((base + parameterFootprint) * 2), 0,
             (codeMaxLocals(t, code) - parameterFootprint) * BytesPerWord * 2);

      sp = base + codeMaxLocals(t, code);
    }
  } goto loop;

 throw_:
  for (; frame; frame = frameNext(t, frame)) {
    code = methodCode(t, frameMethod(t, frame));
    object eht = codeExceptionHandlerTable(t, code);
    if (eht) {
      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
        if (frameIp(t, frame) >= exceptionHandlerStart(eh)
            and frameIp(t, frame) >= exceptionHandlerEnd(eh))
        {
          object catchType = 0;
          if (exceptionHandlerCatchType(eh)) {
            catchType = arrayBody
              (t, codePool(t, code), exceptionHandlerCatchType(eh) - 1);
          }

          if (catchType == 0 or
              (objectClass(t, catchType)
               == arrayBody(t, t->vm->types, Machine::ClassType) and
               instanceOf(t, catchType, exception)))
          {
            sp = frameStackBase(t, frame);
            ip = exceptionHandlerIp(eh);
            pushObject(t, exception);
            exception = 0;
            goto loop;
          }
        }
      }
    }
  }

  for (object e = exception; e; e = throwableCause(t, e)) {
    if (e == exception) {
      fprintf(stderr, "uncaught exception: ");
    } else {
      fprintf(stderr, "caused by: ");
    }

    fprintf(stderr, "%s", &byteArrayBody
            (t, className(t, objectClass(t, exception)), 0));
  
    if (throwableMessage(t, exception)) {
      object m = throwableMessage(t, exception);
      char message[stringLength(t, m) + 1];
      memcpy(message,
             &byteArrayBody(t, stringBytes(t, m), stringOffset(t, m)),
             stringLength(t, m));
      message[stringLength(t, m)] = 0;
      fprintf(stderr, ": %s\n", message);
    } else {
      fprintf(stderr, "\n");
    }

    object trace = throwableTrace(t, e);
    for (unsigned i = 0; i < objectArrayLength(t, trace); ++i) {
      object e = objectArrayBody(t, trace, i);
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, stackTraceElementMethod(t, e))), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, stackTraceElementMethod(t, e)), 0);
      int line = lineNumber
        (t, stackTraceElementMethod(t, e), stackTraceElementIp(t, e));

      fprintf(stderr, "  at %s.%s ", class_, method);

      switch (line) {
      case NativeLine:
        fprintf(stderr, "(native)\n");
        break;
      case UnknownLine:
        fprintf(stderr, "(unknown line)\n");
        break;
      default:
        fprintf(stderr, "(line %d)\n", line);
      }
    }
  }

  return 0;
}

void
run(Thread* t, const char* className, int argc, const char** argv)
{
  enter(t, Thread::ActiveState);

  object class_ = resolveClass(t, makeByteArray(t, "%s", className));
  if (LIKELY(t->exception == 0)) {
    PROTECT(t, class_);

    object name = makeByteArray(t, "main");
    PROTECT(t, name);

    object spec = makeByteArray(t, "([Ljava/lang/String;)V");
    object reference = makeReference(t, class_, name, spec);
    
    object method = findMethodInClass(t, class_, reference);
    if (LIKELY(t->exception == 0)) {
      t->code = methodCode(t, method);
      t->frame = makeFrame(t, method, 0, 0, 0);

      object args = makeObjectArray
        (t, arrayBody(t, t->vm->types, Machine::StringType), argc, true);

      PROTECT(t, args);

      for (int i = 0; i < argc; ++i) {
        object arg = makeString(t, "%s", argv);
        set(t, objectArrayBody(t, args, i), arg);
      }

      pushObject(t, args);
    }    
  }

  run(t);
}

} // namespace

namespace vm {

void
run(System* system, Heap* heap, ClassFinder* classFinder,
    const char* className, int argc, const char** argv)
{
  Machine m(system, heap, classFinder);
  Thread t(&m);

  run(&t, className, argc, argv);
}

}
