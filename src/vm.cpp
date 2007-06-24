#include "common.h"
#include "system.h"
#include "heap.h"
#include "class_finder.h"
#include "stream.h"
#include "constants.h"
#include "vm.h"

#define PROTECT(thread, name)                                   \
  Thread::Protector MAKE_NAME(protector_) (thread, &name);

#define ACQUIRE(t, x) MonitorResource MAKE_NAME(monitorResource_) (t, x)
#define ACQUIRE_RAW(t, x) RawMonitorResource MAKE_NAME(monitorResource_) (t, x)

using namespace vm;

namespace {

static const bool Debug = true;

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
unsigned objectSize(Thread* t, object o);

object&
objectClass(object o)
{
  return cast<object>(o, 0);
}

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
  System::Library* libraries;
  object classMap;
  object bootstrapClassMap;
  object builtinMap;
  object types;
  bool unsafe;
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

class Thread {
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
  object stack[StackSizeInWords];
  object heap[HeapSizeInWords];
  Protector* protector;
  Chain* chain;
};

#include "type-declarations.cpp"
#include "type-constructors.cpp"

void enter(Thread* t, Thread::State state);

class MonitorResource {
 public:
  MonitorResource(Thread* t, System::Monitor* m): t(t), m(m) {
    if (not m->tryAcquire(t)) {
      enter(t, Thread::IdleState);
      m->acquire(t);
      enter(t, Thread::ActiveState);
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
  libraries(0),
  classMap(0),
  bootstrapClassMap(0),
  builtinMap(0),
  types(0),
  unsafe(false)
{
  if (not system->success(system->make(&stateLock)) or
      not system->success(system->make(&heapLock)) or
      not system->success(system->make(&classLock)))
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
  libraries->dispose();
  
  if (rootThread) {
    rootThread->dispose();
  }
}

uint32_t
hash(const int8_t* s, unsigned length)
{
  uint32_t h = 0;
  for (unsigned i = 0; i < length; ++i) h = (h * 31) + s[i];
  return h;  
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
      if (equal(t, tripleFirst(t, n), key)) {
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
hashMapGrow(Thread* t, object map, uint32_t (*hash)(Thread*, object))
{
  PROTECT(t, map);

  object oldArray = hashMapArray(t, map);
  unsigned oldLength = (oldArray ? arrayLength(t, oldArray) : 0);
  PROTECT(t, oldArray);

  unsigned newLength = (oldLength ? oldLength * 2 : 32);
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

    hashMapGrow(t, map, hash);
    array = hashMapArray(t, map);
  }

  unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
  object n = arrayBody(t, array, index);

  n = makeTriple(t, key, value, n);

  set(t, arrayBody(t, array, index), n);
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
push(Thread* t, object o)
{
  t->stack[(t->sp)++] = o;
}

inline void
pushSafe(Thread* t, object o)
{
  expect(t, t->sp + 1 < Thread::StackSizeInWords);
  push(t, o);
}

inline object
pop(Thread* t)
{
  return t->stack[--(t->sp)];
}

inline object&
top(Thread* t)
{
  return t->stack[t->sp - 1];
}

int32_t
builtinToString(Thread* t, int32_t this_)
{
  object o = t->stack[this_ - 1];
  object s = makeString(t, "%s@%p",
                        &byteArrayBody(t, className(t, objectClass(o)), 0),
                        o);
  pushSafe(t, s);
  return t->sp;
}

Thread::Thread(Machine* m):
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
    set(t, objectClass(t->vm->types), arrayClass);

    object classClass = arrayBody(t, m->types, Machine::ClassType);
    set(t, objectClass(classClass), classClass);

    object intArrayClass = arrayBody(t, m->types, Machine::IntArrayType);
    set(t, objectClass(intArrayClass), classClass);

    m->unsafe = false;

    m->bootstrapClassMap = makeHashMap(this, 0, 0);

#include "type-java-initializations.cpp"

    m->classMap = makeHashMap(this, 0, 0);
    m->builtinMap = makeHashMap(this, 0, 0);

    struct {
      const char* key;
      void* value;
    } builtins[] = {
      { "Java_java_lang_Object_toString",
        reinterpret_cast<void*>(builtinToString) },
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
    v->visit(t->stack + i);
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
      v->visit(&(m->types));

      for (Thread* t = m->rootThread; t; t = t->next) {
        ::visitRoots(t, v);
      }
    }

    virtual unsigned sizeInWords(void* p) {
      Thread* t = m->rootThread;

      p = m->heap->follow(p);
      object class_ = m->heap->follow(objectClass(p));

      unsigned n = divide(classFixedSize(t, class_), BytesPerWord);

      if (classArrayElementSize(t, class_)) {
        n += divide(classArrayElementSize(t, class_)
                    * cast<uint32_t>(p, classFixedSize(t, class_) - 4),
                    BytesPerWord);
      }
      return n;
    }

    virtual void walk(void* p, Heap::Walker* w) {
      Thread* t = m->rootThread;

      p = m->heap->follow(p);
      object class_ = m->heap->follow(objectClass(p));
      object objectMask = m->heap->follow(classObjectMask(t, class_));

      if (objectMask) {
//         fprintf(stderr, "p: %p; class: %p; mask: %p; mask length: %d\n",
//                 p, class_, objectMask, intArrayLength(t, objectMask));

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
          if (mask[wordOf(i)] & (static_cast<uintptr_t>(1) << bitOf(i))) {
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
      t->vm->stateLock->wait(t);
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
        t->vm->stateLock->wait(t);
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
      t->vm->stateLock->wait(t);
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
  unsigned sizeInBytes = classFixedSize(t, class_);
  object instance = allocate(t, sizeInBytes);
  *static_cast<object*>(instance) = class_;
  memset(static_cast<object*>(instance) + sizeof(object), 0,
         sizeInBytes - sizeof(object));
  return instance;
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
makeTrace(Thread* t)
{
  object trace = 0;
  if (t->frame) {
    PROTECT(t, trace);
    frameIp(t, t->frame) = t->ip;
    for (; t->frame; t->frame = frameNext(t, t->frame)) {
      trace = makeTrace
        (t, frameMethod(t, t->frame), frameIp(t, t->frame), trace);
    }
  }
  return trace;
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

inline bool
isLongOrDouble(Thread* t, object o)
{
  return objectClass(o) == arrayBody(t, t->vm->types, Machine::LongType)
    or objectClass(o) == arrayBody(t, t->vm->types, Machine::DoubleType);
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
  case 'Z':
    return BooleanField;
  case 'L':
  case '[':
    return ObjectField;

  default: abort(t);
  }
}

uint64_t
primitiveValue(Thread* t, unsigned code, object o)
{
  switch (code) {
  case ByteField:
    return byteValue(t, o);
  case CharField:
    return charValue(t, o);
  case DoubleField:
    return doubleValue(t, o);
  case FloatField:
    return floatValue(t, o);
  case IntField:
    return intValue(t, o);
  case LongField:
    return longValue(t, o);
  case ShortField:
    return shortValue(t, o);
  case BooleanField:
    return booleanValue(t, o);

  default: abort(t);
  }
}

object
makePrimitive(Thread* t, unsigned code, uint64_t value)
{
  switch (code) {
  case ByteField:
    return makeByte(t, value);
  case CharField:
    return makeChar(t, value);
  case DoubleField:
    return makeDouble(t, value);
  case FloatField:
    return makeFloat(t, value);
  case IntField:
    return makeInt(t, value);
  case LongField:
    return makeLong(t, value);
  case ShortField:
    return makeShort(t, value);
  case BooleanField:
    return makeBoolean(t, value);

  default: abort(t);
  }
}

unsigned
primitiveSize(Thread* t, unsigned code)
{
  switch (code) {
  case ByteField:
  case BooleanField:
    return 1;
  case CharField:
  case ShortField:
    return 2;
  case DoubleField:
  case LongField:
    return 8;
  case FloatField:
  case IntField:
    return 4;

  default: abort(t);
  }
}

object
getField(Thread* t, object instance, object field)
{
  switch (fieldCode(t, field)) {
  case ByteField:
    return makeByte(t, cast<int8_t>(instance, fieldOffset(t, field)));
  case CharField:
    return makeChar(t, cast<int16_t>(instance, fieldOffset(t, field)));
  case DoubleField:
    return makeDouble(t, cast<int64_t>(instance, fieldOffset(t, field)));
  case FloatField:
    return makeFloat(t, cast<int32_t>(instance, fieldOffset(t, field)));
  case IntField:
    return makeInt(t, cast<int32_t>(instance, fieldOffset(t, field)));
  case LongField:
    return makeLong(t, cast<int64_t>(instance, fieldOffset(t, field)));
  case ShortField:
    return makeShort(t, cast<int16_t>(instance, fieldOffset(t, field)));
  case BooleanField:
    return makeBoolean(t, cast<int8_t>(instance, fieldOffset(t, field)));
  case ObjectField:
    return cast<object>(instance, fieldOffset(t, field));

  default: abort(t);
  }
}

void
setField(Thread* t, object o, object field, object value)
{
  switch (fieldCode(t, field)) {
  case ByteField:
    cast<int8_t>(o, fieldOffset(t, field)) = byteValue(t, value);
    break;
  case CharField:
    cast<int16_t>(o, fieldOffset(t, field)) = charValue(t, value);
    break;
  case DoubleField:
    cast<int64_t>(o, fieldOffset(t, field)) = doubleValue(t, value);
    break;
  case FloatField:
    cast<int32_t>(o, fieldOffset(t, field)) = floatValue(t, value);
    break;
  case IntField:
    cast<int32_t>(o, fieldOffset(t, field)) = intValue(t, value);
    break;
  case LongField:
    cast<int64_t>(o, fieldOffset(t, field)) = longValue(t, value);
    break;
  case ShortField:
    cast<int16_t>(o, fieldOffset(t, field)) = shortValue(t, value);
    break;
  case BooleanField:
    cast<int8_t>(o, fieldOffset(t, field)) = booleanValue(t, value);
    break;
  case ObjectField:
    set(t, cast<object>(o, fieldOffset(t, field)), value);

  default: abort(t);
  }
}

inline object
getStatic(Thread* t, object field)
{
  return arrayBody(t, classStaticTable(t, fieldClass(t, field)),
                   fieldOffset(t, field));
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

  if (objectClass(class_)
      == arrayBody(t, t->vm->types, Machine::InterfaceType))
  {
    for (object oc = objectClass(o); oc; oc = classSuper(t, oc)) {
      object itable = classInterfaceTable(t, oc);
      for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
        if (arrayBody(t, itable, i) == class_) {
          return true;
        }
      }
    }
  } else {
    for (object oc = objectClass(o); oc; oc = classSuper(t, oc)) {
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
  object itable = classInterfaceTable(t, objectClass(o));
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
  return findMethod(t, method, objectClass(o));
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
    } break;

    case CONSTANT_Double: {
      object value = makeLong(t, s.readDouble());
      set(t, arrayBody(t, pool, i), value);
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
    if (objectClass(o) == arrayBody(t, t->vm->types, Machine::IntArrayType)) {
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
    if (objectClass(o) == arrayBody(t, t->vm->types, Machine::IntArrayType)) {
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
parseInterfaceTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);
  
  object map = makeHashMap(t, 0, 0);
  PROTECT(t, map);

  if (classSuper(t, class_)) {
    object superInterfaces = classInterfaceTable(t, classSuper(t, class_));
    if (superInterfaces) {
      PROTECT(t, superInterfaces);

      for (unsigned i = 0; i < arrayLength(t, superInterfaces); i += 2) {
        object name = interfaceName(t, arrayBody(t, superInterfaces, i));
        hashMapInsert(t, map, name, name, byteArrayHash);
      }
    }
  }
  
  unsigned count = s.read2();
  for (unsigned i = 0; i < count; ++i) {
    object name = arrayBody(t, pool, s.read2() - 1);
    hashMapInsert(t, map, name, name, byteArrayHash);
  }

  object interfaceTable = 0;
  if (hashMapSize(t, map)) {
    interfaceTable = makeArray(t, hashMapSize(t, map), true);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    object it = hashMapIterator(t, map);
    PROTECT(t, it);

    for (; it; it = hashMapIteratorNext(t, it)) {
      object interface = resolveClass
        (t, tripleFirst(t, hashMapIteratorNode(t, it)));
      if (UNLIKELY(t->exception)) return;

      set(t, arrayBody(t, interfaceTable, i++), interface);

      // we'll fill in this table in parseMethodTable():
      object vtable = makeArray
        (t, arrayLength(t, interfaceMethodTable(t, interface)), true);

      set(t, arrayBody(t, interfaceTable, i++), vtable);      
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

  classFixedSize(t, class_) = divide(memberOffset, BytesPerWord);
  
  object mask = makeIntArray
    (t, divide(classFixedSize(t, class_), BitsPerWord), true);

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

  object code = makeCode(t, pool, 0, maxStack, maxLocals, length, false);
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
    s.read2();
    s.skip(s.read4());
  }

  return code;
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

object
makeJNIName(Thread* t, object method, bool /*decorate*/)
{
  object name = makeByteArray
    (t, "Java_%s_%s",
     &byteArrayBody(t, className(t, methodClass(t, method)), 0),
     &byteArrayBody(t, methodName(t, method), 0));

  for (unsigned i = 0; i < byteArrayLength(t, name) - 1; ++i) {
    switch (byteArrayBody(t, name, i)) {
    case '/':
      byteArrayBody(t, name, i) = '_';
      break;
    }
  }

  // todo: decorate and translate as needed
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

      if ((flags & ACC_STATIC) == 0) {
        ++ parameterCount;
      }

      object method = makeMethod(t,
                                 flags,
                                 0, // offset
                                 parameterCount,
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

    object vtable = makeArray(t, virtualCount, false);

    unsigned i = 0;
    if (superVirtualTable) {
      for (; i < arrayLength(t, superVirtualTable); ++i) {
        object method = arrayBody(t, superVirtualTable, i);
        method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);

        set(t, arrayBody(t, vtable, i), method);
      }
    }

    for (object p = listFront(t, newVirtuals); p; p = pairSecond(t, p)) {
      set(t, arrayBody(t, vtable, i++), pairFirst(t, p));        
    }

    set(t, classVirtualTable(t, class_), vtable);

    // generate interface vtables
    
    object itable = classInterfaceTable(t, class_);
    if (itable) {
      PROTECT(t, itable);

      for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
        object methodTable = interfaceMethodTable(t, arrayBody(t, itable, i));
        object vtable = arrayBody(t, itable, i + 1);
        
        for (unsigned j = 0; j < arrayLength(t, methodTable); ++j) {
          object method = arrayBody(t, methodTable, j);
          method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);
          
          set(t, arrayBody(t, vtable, j), method);        
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

  enter(t, Thread::ExclusiveState);

  memcpy(bootstrapClass, class_, objectSize(t, class_));

  enter(t, Thread::ActiveState);
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
      fprintf(stderr, "parsing %s\n", &byteArrayBody
              (t, spec, 0));

      // parse class file
      class_ = parseClass(t, data->start(), data->length());
      data->dispose();

      fprintf(stderr, "done parsing %s\n", &byteArrayBody
              (t, className(t, class_), 0));

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
  if (objectClass(o) == arrayBody(t, t->vm->types, Machine::ByteArrayType)) {
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
  if (objectClass(o) == arrayBody(t, t->vm->types, Machine::ByteArrayType)) {
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
  if (objectClass(o) == arrayBody(t, t->vm->types, Machine::ReferenceType)) {
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
                                     methodParameterCount(t, method),
                                     false);
        
  unsigned argumentTableSize = sizeof(void*) / 4;
  unsigned index = 0;

  if ((methodFlags(t, method) & ACC_STATIC) == 0) {
    nativeMethodDataParameterCodes(t, data, index++) = ObjectField;
    argumentTableSize += 1;    
  }

  const char* s = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));
  ++ s; // skip '('
  while (*s and *s != ')') {
    unsigned code = fieldCode(t, *s);
    nativeMethodDataParameterCodes(t, data, index++) = code;

    switch (*s) {
    case 'L':
      argumentTableSize += 1;
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      argumentTableSize += 1;
      while (*s == '[') ++ s;
      break;
      
    default:
      argumentTableSize += divide(primitiveSize(t, code), 4);
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
  if (objectClass(methodCode(t, method))
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

    if (data) {
      set(t, methodCode(t, method), data);
    }
    return data;
  } else {
    return methodCode(t, method);
  }  
}

inline object
invokeNative(Thread* t, object method)
{
  object data = resolveNativeMethodData(t, method);
  if (UNLIKELY(data == 0)) {
    object message = makeString
      (t, "%s.%s:%s",
       &byteArrayBody(t, className(t, methodClass(t, method)), 0),
       &byteArrayBody(t, methodName(t, method), 0),
       &byteArrayBody(t, methodSpec(t, method), 0));
    t->exception = makeUnsatisfiedLinkError(t, message);
    return 0;
  }

  unsigned parameterCount = methodParameterCount(t, method);

  uint32_t args[nativeMethodDataArgumentTableSize(t, data)];
  uint8_t sizes[parameterCount + 1];
  unsigned offset = 0;

  switch (sizeof(uintptr_t)) {
  case 4: {
    sizes[0] = 4;
    args[offset++] = reinterpret_cast<uintptr_t>(t);
  } break;

  case 8: {
    sizes[0] = 8;
    uint64_t v = reinterpret_cast<uint64_t>(t);
    args[offset++] = static_cast<uint32_t>(v >> 32);
    args[offset++] = static_cast<uint32_t>(v & 0xFFFFFFFF);
  } break;
    
  default:
    abort(t);
  }

  for (unsigned i = 0; i < parameterCount; ++i) {
    unsigned code = nativeMethodDataParameterCodes(t, data, i);

    if (code == ObjectField) {
      sizes[i + 1] = 4;
      args[offset++] = t->sp + i + 1;
    } else {
      sizes[i + 1] = primitiveSize(t, code);
      uint64_t v = primitiveValue(t, code, t->stack[t->sp + i]);
      if (sizes[i + 1] == 8) {
        args[offset++] = static_cast<uint32_t>(v >> 32);
        args[offset++] = static_cast<uint32_t>(v & 0xFFFFFFFF);
      } else {
        args[offset++] = v;
      }
    }
  }

  unsigned returnCode = nativeMethodDataReturnCode(t, data);
  unsigned returnSize
    = (returnCode == ObjectField ? 4 : primitiveSize(t, returnCode));

  void* function = nativeMethodDataFunction(t, data);

  bool builtin = nativeMethodDataBuiltin(t, data);
  if (not builtin) {
    enter(t, Thread::IdleState);
  }

  uint64_t rv = t->vm->system->call(function,
                                    parameterCount,
                                    args,
                                    sizes,
                                    returnSize);

  if (not builtin) {
    enter(t, Thread::ActiveState);
  }

  if (UNLIKELY(t->exception) or returnCode == VoidField) {
    return 0;
  } else if (returnCode == ObjectField) {
    return (rv == 0 ? 0 : t->stack[rv - 1]);
  } else {
    return makePrimitive(t, returnCode, rv);
  }
}

object
run(Thread* t)
{
  unsigned& ip = t->ip;
  unsigned& sp = t->sp;
  object& code = t->code;
  object& frame = t->frame;
  object& exception = t->exception;
  object* stack = t->stack;

  if (UNLIKELY(exception)) goto throw_;

 loop:
  //fprintf(stderr, "ip: %d; instruction: 0x%x\n", ip, codeBody(t, code, ip));

  switch (codeBody(t, code, ip++)) {
  case aaload: {
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < objectArrayLength(t, array)))
      {
        push(t, objectArrayBody(t, array, i));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < objectArrayLength(t, array)))
      {
        set(t, objectArrayBody(t, array, i), value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    push(t, 0);
  } goto loop;

  case aload:
  case iload:
  case lload: {
    push(t, frameLocals(t, frame, codeBody(t, code, ip++)));
  } goto loop;

  case aload_0:
  case iload_0:
  case lload_0: {
    push(t, frameLocals(t, frame, 0));
  } goto loop;

  case aload_1:
  case iload_1:
  case lload_1: {
    push(t, frameLocals(t, frame, 1));
  } goto loop;

  case aload_2:
  case iload_2:
  case lload_2: {
    push(t, frameLocals(t, frame, 2));
  } goto loop;

  case aload_3:
  case iload_3:
  case lload_3: {
    push(t, frameLocals(t, frame, 3));
  } goto loop;

  case anewarray: {
    object count = pop(t);
    int32_t c = intValue(t, count);

    if (LIKELY(c >= 0)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      object array = makeObjectArray(t, class_, c, true);
      
      push(t, array);
    } else {
      object message = makeString(t, "%d", c);
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
      object value = pop(t);
      code = 0;
      return value;
    }
  } goto loop;

  case arraylength: {
    object array = pop(t);
    if (LIKELY(array)) {
      if (objectClass(array)
          == arrayBody(t, t->vm->types, Machine::ObjectArrayType))
      {
        push(t, makeInt(t, objectArrayLength(t, array)));
      } else {
        // for all other array types, the length follow the class pointer.
        push(t, makeInt(t, cast<uint32_t>(array, BytesPerWord)));
      }
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } abort(t);

  case astore:
  case istore:
  case lstore: {
    object value = pop(t);
    set(t, frameLocals(t, frame, codeBody(t, code, ip++)), value);
  } goto loop;

  case astore_0:
  case istore_0:
  case lstore_0: {
    object value = pop(t);
    set(t, frameLocals(t, frame, 0), value);
  } goto loop;

  case astore_1:
  case istore_1:
  case lstore_1: {
    object value = pop(t);
    set(t, frameLocals(t, frame, 1), value);
  } goto loop;

  case astore_2:
  case istore_2:
  case lstore_2: {
    object value = pop(t);
    set(t, frameLocals(t, frame, 2), value);
  } goto loop;

  case astore_3:
  case istore_3:
  case lstore_3: {
    object value = pop(t);
    set(t, frameLocals(t, frame, 3), value);
  } goto loop;

  case athrow: {
    exception = pop(t);
    if (UNLIKELY(exception == 0)) {
      exception = makeNullPointerException(t);      
    }
    goto throw_;
  } abort(t);

  case baload: {
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < byteArrayLength(t, array)))
      {
        push(t, makeByte(t, byteArrayBody(t, array, i)));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < byteArrayLength(t, array)))
      {
        byteArrayBody(t, array, i) = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    push(t, makeInt(t, codeBody(t, code, ip++)));
  } goto loop;

  case caload: {
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < charArrayLength(t, array)))
      {
        push(t, makeInt(t, charArrayBody(t, array, i)));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < charArrayLength(t, array)))
      {
        charArrayBody(t, array, i) = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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

    if (stack[sp - 1]) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (not instanceOf(t, class_, stack[sp - 1])) {
        object message = makeString
          (t, "%s as %s",
           &byteArrayBody(t, className(t, objectClass(stack[sp - 1])), 0),
           &byteArrayBody(t, className(t, class_), 0));
        exception = makeClassCastException(t, message);
        goto throw_;
      }
    }
  } goto loop;

  case dup: {
    object value = stack[sp - 1];
    push(t, value);
  } goto loop;

  case dup_x1: {
    object first = pop(t);
    object second = pop(t);
    
    push(t, first);
    push(t, second);
    push(t, first);
  } goto loop;

  case dup_x2: {
    object first = pop(t);
    object second = pop(t);
    object third = pop(t);
    
    push(t, first);
    push(t, third);
    push(t, second);
    push(t, first);
  } goto loop;

  case dup2: {
    object first = stack[sp - 1];
    if (isLongOrDouble(t, first)) {
      push(t, first);
    } else {
      object second = stack[sp - 2];
      push(t, second);
      push(t, first);
    }
  } goto loop;

  case dup2_x1: {
    object first = pop(t);
    object second = pop(t);
    
    if (isLongOrDouble(t, first)) {
      push(t, first);
      push(t, second);
      push(t, first);
    } else {
      object third = pop(t);
      push(t, second);
      push(t, first);
      push(t, third);
      push(t, second);
      push(t, first);
    }
  } goto loop;

  case dup2_x2: {
    object first = pop(t);
    object second = pop(t);
    
    if (isLongOrDouble(t, first)) {
      if (isLongOrDouble(t, second)) {
        push(t, first);
        push(t, second);
        push(t, first);
      } else {
        object third = pop(t);
        push(t, first);
        push(t, third);
        push(t, second);
        push(t, first);
      }
    } else {
      object third = pop(t);
      if (isLongOrDouble(t, third)) {
        push(t, second);
        push(t, first);
        push(t, third);
        push(t, second);
        push(t, first);
      } else {
        object fourth = pop(t);
        push(t, second);
        push(t, first);
        push(t, fourth);
        push(t, third);
        push(t, second);
        push(t, first);
      }
    }
  } goto loop;

  case getfield: {
    object instance = pop(t);
    if (LIKELY(instance)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      push(t, getField(t, instance, field));
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
      
    push(t, getStatic(t, field));
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
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<int8_t>(intValue(t, v))));
  } goto loop;

  case i2c: {
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<uint16_t>(intValue(t, v))));
  } goto loop;

  case i2l: {
    object v = pop(t);
    
    push(t, makeLong(t, intValue(t, v)));
  } goto loop;

  case i2s: {
    object v = pop(t);

    push(t, makeInt(t, static_cast<int16_t>(intValue(t, v))));
  } goto loop;

  case iadd: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) + intValue(t, b)));
  } goto loop;

  case iaload: {
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < intArrayLength(t, array)))
      {
        push(t, makeInt(t, intArrayBody(t, array, i)));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) & intValue(t, b)));
  } goto loop;

  case iastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < intArrayLength(t, array)))
      {
        intArrayBody(t, array, i) = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    push(t, makeInt(t, 0));
  } goto loop;

  case iconst_1: {
    push(t, makeInt(t, 1));
  } goto loop;

  case iconst_2: {
    push(t, makeInt(t, 2));
  } goto loop;

  case iconst_3: {
    push(t, makeInt(t, 3));
  } goto loop;

  case iconst_4: {
    push(t, makeInt(t, 4));
  } goto loop;

  case iconst_5: {
    push(t, makeInt(t, 5));
  } goto loop;

  case idiv: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) / intValue(t, b)));
  } goto loop;

  case if_acmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (a == b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_acmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (a != b) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) == intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) != intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) > intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmpge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) >= intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmplt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) < intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case if_icmple: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object b = pop(t);
    object a = pop(t);
    
    if (intValue(t, a) < intValue(t, b)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifeq: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v) == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifne: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v)) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifgt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v) > 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifge: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v) >= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iflt: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v) < 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifle: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (intValue(t, v) <= 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnonnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (v) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case ifnull: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    object v = pop(t);
    
    if (v == 0) {
      ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
    }
  } goto loop;

  case iinc: {
    uint8_t index = codeBody(t, code, ip++);
    int8_t c = codeBody(t, code, ip++);
    
    int32_t v = intValue(t, frameLocals(t, frame, index));
    set(t, frameLocals(t, frame, index), makeInt(t, v + c));
  } goto loop;

  case imul: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) * intValue(t, b)));
  } goto loop;

  case ineg: {
    object v = pop(t);
    
    push(t, makeInt(t, - intValue(t, v)));
  } goto loop;

  case instanceof: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    if (stack[sp - 1]) {
      uint16_t index = (index1 << 8) | index2;
      
      object class_ = resolveClass(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;

      if (instanceOf(t, class_, stack[sp - 1])) {
        push(t, makeInt(t, 1));
      } else {
        push(t, makeInt(t, 0));
      }
    } else {
      push(t, makeInt(t, 0));
    }
  } goto loop;

  case invokeinterface: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;
      
    ip += 2;

    object method = resolveMethod(t, codePool(t, code), index - 1);
    if (UNLIKELY(exception)) goto throw_;
    
    unsigned parameterCount = methodParameterCount(t, method);
    if (LIKELY(stack[sp - parameterCount])) {
      code = findInterfaceMethod(t, method, stack[sp - parameterCount]);
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
    
    unsigned parameterCount = methodParameterCount(t, method);
    if (LIKELY(stack[sp - parameterCount])) {
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
    
    unsigned parameterCount = methodParameterCount(t, method);
    if (LIKELY(stack[sp - parameterCount])) {
      code = findVirtualMethod(t, method, stack[sp - parameterCount]);
      if (UNLIKELY(exception)) goto throw_;
      
      goto invoke;
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
    }
  } goto loop;

  case ior: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) | intValue(t, b)));
  } goto loop;

  case irem: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) % intValue(t, b)));
  } goto loop;

  case ishl: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) << intValue(t, b)));
  } goto loop;

  case ishr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) >> intValue(t, b)));
  } goto loop;

  case isub: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) - intValue(t, b)));
  } goto loop;

  case iushr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, static_cast<uint32_t>(intValue(t, a)) >> intValue(t, b)));
  } goto loop;

  case ixor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, intValue(t, a) ^ intValue(t, b)));
  } goto loop;

  case jsr: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);

    push(t, makeInt(t, ip));
    ip = (ip - 3) + static_cast<int16_t>(((offset1 << 8) | offset2));
  } goto loop;

  case jsr_w: {
    uint8_t offset1 = codeBody(t, code, ip++);
    uint8_t offset2 = codeBody(t, code, ip++);
    uint8_t offset3 = codeBody(t, code, ip++);
    uint8_t offset4 = codeBody(t, code, ip++);

    push(t, makeInt(t, ip));
    ip = (ip - 3) + static_cast<int32_t>
      ((offset1 << 24) | (offset2 << 16) | (offset3 << 8) | offset4);
  } goto loop;

  case l2i: {
    object v = pop(t);
    
    push(t, makeInt(t, static_cast<int32_t>(longValue(t, v))));
  } goto loop;

  case ladd: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) + longValue(t, b)));
  } goto loop;

  case laload: {
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < longArrayLength(t, array)))
      {
        push(t, makeLong(t, longArrayBody(t, array, i)));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) & longValue(t, b)));
  } goto loop;

  case lastore: {
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < longArrayLength(t, array)))
      {
        longArrayBody(t, array, i) = longValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeInt(t, longValue(t, a) > longValue(t, b) ? 1
                    : longValue(t, a) == longValue(t, b) ? 0 : -1));
  } goto loop;

  case lconst_0: {
    push(t, makeLong(t, 0));
  } goto loop;

  case lconst_1: {
    push(t, makeLong(t, 1));
  } goto loop;

  case ldc: {
    push(t, arrayBody(t, codePool(t, code), codeBody(t, code, ip++) - 1));
  } goto loop;

  case ldc_w:
  case ldc2_w: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    push(t, arrayBody(t, codePool(t, code), ((index1 << 8) | index2) - 1));
  } goto loop;

  case vm::ldiv: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) / longValue(t, b)));
  } goto loop;

  case lmul: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) * longValue(t, b)));
  } goto loop;

  case lneg: {
    object v = pop(t);
    
    push(t, makeLong(t, - longValue(t, v)));
  } goto loop;

  case lor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) | longValue(t, b)));
  } goto loop;

  case lrem: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) % longValue(t, b)));
  } goto loop;

  case lshl: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) << longValue(t, b)));
  } goto loop;

  case lshr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) >> longValue(t, b)));
  } goto loop;

  case lsub: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) - longValue(t, b)));
  } goto loop;

  case lushr: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, static_cast<uint64_t>(longValue(t, a))
                     << longValue(t, b)));
  } goto loop;

  case lxor: {
    object b = pop(t);
    object a = pop(t);
    
    push(t, makeLong(t, longValue(t, a) ^ longValue(t, b)));
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

    push(t, make(t, class_));
  } goto loop;

  case newarray: {
    object count = pop(t);
    int32_t c = intValue(t, count);

    if (LIKELY(c >= 0)) {
      uint8_t type = codeBody(t, code, ip++);

      object array;

      switch (type) {
      case T_BOOLEAN:
        array = makeBooleanArray(t, c, true);
        break;

      case T_CHAR:
        array = makeCharArray(t, c, true);
        break;

      case T_FLOAT:
        array = makeFloatArray(t, c, true);
        break;

      case T_DOUBLE:
        array = makeDoubleArray(t, c, true);
        break;

      case T_BYTE:
        array = makeByteArray(t, c, true);
        break;

      case T_SHORT:
        array = makeShortArray(t, c, true);
        break;

      case T_INT:
        array = makeIntArray(t, c, true);
        break;

      case T_LONG:
        array = makeLongArray(t, c, true);
        break;

      default: abort(t);
      }
            
      push(t, array);
    } else {
      object message = makeString(t, "%d", c);
      exception = makeNegativeArrayStoreException(t, message);
      goto throw_;
    }
  } goto loop;

  case nop: goto loop;

  case vm::pop: {
    -- sp;
  } goto loop;

  case pop2: {
    object top = stack[sp - 1];
    if (isLongOrDouble(t, top)) {
      -- sp;
    } else {
      sp -= 2;
    }
  } goto loop;

  case putfield: {
    object instance = pop(t);
    if (LIKELY(instance)) {
      uint8_t index1 = codeBody(t, code, ip++);
      uint8_t index2 = codeBody(t, code, ip++);
      uint16_t index = (index1 << 8) | index2;
    
      object field = resolveField(t, codePool(t, code), index - 1);
      if (UNLIKELY(exception)) goto throw_;
      
      object value = pop(t);
      setField(t, instance, field, value);
    } else {
      exception = makeNullPointerException(t);
      goto throw_;
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
      
    object value = pop(t);
    setStatic(t, field, value);
  } goto loop;

  case ret: {
    ip = intValue(t, frameLocals(t, frame, codeBody(t, code, ip)));
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
    object index = pop(t);
    object array = pop(t);

    if (LIKELY(array)) {
      int32_t i = intValue(t, index);
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < shortArrayLength(t, array)))
      {
        push(t, makeShort(t, shortArrayBody(t, array, i)));
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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
    object value = pop(t);
    object index = pop(t);
    object array = pop(t);
    int32_t i = intValue(t, index);

    if (LIKELY(array)) {
      if (LIKELY(i >= 0 and
                 static_cast<uint32_t>(i) < shortArrayLength(t, array)))
      {
        shortArrayBody(t, array, i) = intValue(t, value);
      } else {
        object message = makeString(t, "%d not in [0,%d]", i,
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

    push(t, makeInt(t, (byte1 << 8) | byte2));
  } goto loop;

  case swap: {
    object tmp = stack[sp - 1];
    stack[sp - 1] = stack[sp - 2];
    stack[sp - 2] = tmp;
  } goto loop;

  case wide: goto wide;

  default: abort(t);
  }

 wide:
  switch (codeBody(t, code, ip++)) {
  case aload:
  case iload:
  case lload: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    push(t, frameLocals(t, frame, (index1 << 8) | index2));
  } goto loop;

  case astore:
  case istore:
  case lstore: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    object value = pop(t);
    set(t, frameLocals(t, frame, (index1 << 8) | index2), value);
  } goto loop;

  case iinc: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);
    uint16_t index = (index1 << 8) | index2;

    uint8_t count1 = codeBody(t, code, ip++);
    uint8_t count2 = codeBody(t, code, ip++);
    uint16_t count = (count1 << 8) | count2;
    
    int32_t v = intValue(t, frameLocals(t, frame, index));
    set(t, frameLocals(t, frame, index), makeInt(t, v + count));
  } goto loop;

  case ret: {
    uint8_t index1 = codeBody(t, code, ip++);
    uint8_t index2 = codeBody(t, code, ip++);

    ip = intValue(t, frameLocals(t, frame, (index1 << 8) | index2));
  } goto loop;

  default: abort(t);
  }

 invoke: {    
    unsigned parameterCount = methodParameterCount(t, code);
    unsigned base = sp - parameterCount;

    if (UNLIKELY(codeMaxStack(t, methodCode(t, code)) + base
                 > Thread::StackSizeInWords))
    {
      exception = makeStackOverflowError(t);
      goto throw_;      
    }

    if (methodFlags(t, code) & ACC_NATIVE) {
      object r = invokeNative(t, code);

      if (UNLIKELY(exception)) {
        goto throw_;
      }

      sp = base;
      if (nativeMethodDataReturnCode(t, methodCode(t, code)) != VoidField) {
        push(t, r);
      }
    } else {
      frameIp(t, frame) = ip;
      ip = 0;

      frame = makeFrame(t, code, frame, 0, base,
                        codeMaxLocals(t, methodCode(t, code)), false);
      code = methodCode(t, code);

      memcpy(&frameLocals(t, frame, 0), stack + base,
             parameterCount * BytesPerWord);

      memset(&frameLocals(t, frame, 0) + parameterCount, 0,
             (frameLength(t, frame) - parameterCount) * BytesPerWord);

      sp = base;
    }
  } goto loop;

 throw_:
  for (; frame; frame = frameNext(t, frame)) {
    code = methodCode(t, frameMethod(t, frame));
    object eht = codeExceptionHandlerTable(t, code);
    if (eht) {
      for (unsigned i = 0; i < exceptionHandlerTableLength(t, eht); ++i) {
        ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
        object catchType =
          arrayBody(t, codePool(t, code), exceptionHandlerCatchType(eh) - 1);

        if (catchType == 0 or
            (objectClass(catchType)
             == arrayBody(t, t->vm->types, Machine::ClassType) and
             instanceOf(t, catchType, exception)))
        {
          sp = frameStackBase(t, frame);
          ip = exceptionHandlerIp(eh);
          push(t, exception);
          exception = 0;
          goto loop;
        }
      }
    }
  }

  object p = 0;
  object n = 0;
  for (object trace = throwableTrace(t, exception); trace; trace = n) {
    n = traceNext(t, trace);
    set(t, traceNext(t, trace), p);
    p = trace;
  }

  for (object e = exception; e; e = throwableCause(t, e)) {
    if (e == exception) {
      fprintf(stderr, "uncaught exception: ");
    } else {
      fprintf(stderr, "caused by: ");
    }

    fprintf(stderr, "%s", &byteArrayBody
            (t, className(t, objectClass(exception)), 0));
  
    if (throwableMessage(t, exception)) {
      fprintf(stderr, ": %s\n", &byteArrayBody
              (t, stringBytes(t, throwableMessage(t, exception)), 0));
    }

    for (; p; p = traceNext(t, p)) {
      fprintf(stderr, "  at %s\n", &byteArrayBody
              (t, methodName(t, traceMethod(t, p)), 0));
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
      t->frame = makeFrame
        (t, method, 0, 0, 0, codeMaxLocals(t, t->code), true);

      object args = makeObjectArray
        (t, arrayBody(t, t->vm->types, Machine::StringType), argc, true);

      PROTECT(t, args);

      for (int i = 0; i < argc; ++i) {
        object arg = makeString(t, "%s", argv);
        set(t, objectArrayBody(t, args, i), arg);
      }

      push(t, args);
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
