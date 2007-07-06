#include "jnienv.h"
#include "builtin.h"
#include "machine.h"

using namespace vm;

namespace {

void
visitRoots(Thread* t, Heap::Visitor* v)
{
  t->heapIndex = 0;

  v->visit(&(t->thread));
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
removeMonitor(Thread* t, object o)
{
  hashMapRemove(t, t->vm->monitorMap, o, objectHash, referenceEqual);
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

} // namespace

namespace vm {

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
  jni::populate(&jniEnvVTable);

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
  vtable(&(m->jniEnvVTable)),
  vm(m),
  next(0),
  child(0),
  state(NoState),
  thread(0),
  code(0),
  exception(0),
  ip(0),
  sp(0),
  frame(-1),
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

    builtin::populate(t, m->builtinMap);
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

object
makeByteArray(Thread* t, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  object s = ::makeByteArray(t, format, a);
  va_end(a);

  return s;
}

object
makeString(Thread* t, const char* format, ...)
{
  va_list a;
  va_start(a, format);
  object s = ::makeByteArray(t, format, a);
  va_end(a);

  return makeString(t, s, 0, byteArrayLength(t, s), 0);
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
makeTrace(Thread* t, int frame)
{
  unsigned count = 0;
  for (int f = frame; f >= 0; f = frameNext(t, f)) {
    ++ count;
  }

  object trace = makeObjectArray
    (t, arrayBody(t, t->vm->types, Machine::StackTraceElementType),
     count, true);
  PROTECT(t, trace);

  unsigned index = 0;
  for (int f = frame; f >= 0; f = frameNext(t, f)) {
    object e = makeStackTraceElement(t, frameMethod(t, f), frameIp(t, f));
    set(t, objectArrayBody(t, trace, index++), e);
  }

  return trace;
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

void
addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object))
{
  PROTECT(t, target);

  ACQUIRE(t, t->vm->finalizerLock);

  object p = makePointer(t, reinterpret_cast<void*>(finalize));
  t->vm->finalizers = makeTriple(t, target, p, t->vm->finalizers);
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

#include "type-constructors.cpp"

} // namespace vm
