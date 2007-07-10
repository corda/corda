#include "jnienv.h"
#include "builtin.h"
#include "machine.h"

using namespace vm;

namespace {

bool
find(Thread* t, Thread* o)
{
  if (t == o) return true;

  for (Thread* p = t->peer; p; p = p->peer) {
    if (p == o) return true;
  }

  if (t->child) return find(t->child, o);

  return false;
}

void
join(Thread* t, Thread* o)
{
  if (t != o) {
    o->systemThread->join();
  }
}

void
dispose(Thread* t, Thread* o, bool remove)
{
  if (remove) {
    if (o->parent) {
      if (o->child) {
        o->parent->child = o->child;
        if (o->peer) {
          o->peer->peer = o->child->peer;
          o->child->peer = o->peer;
        }
      } else if (o->peer) {
        o->parent->child = o->peer;
      } else {
        o->parent->child = 0;
      }
    } else if (o->child) {
      t->vm->rootThread = o->child;
      if (o->peer) {
        o->peer->peer = o->child->peer;
        o->child->peer = o->peer;
      }      
    } else if (o->peer) {
      t->vm->rootThread = o->peer;
    } else {
      abort(t);
    }

    assert(t, not find(t->vm->rootThread, o));
  }

  o->dispose();
}

void
joinAll(Thread* m, Thread* o)
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    joinAll(m, child);
  }

  join(m, o);
}

void
disposeAll(Thread* m, Thread* o)
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    disposeAll(m, child);
  }

  dispose(m, o, false);
}

void
killZombies(Thread* t, Thread* o)
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    killZombies(t, child);
  }

  if (o->state == Thread::ZombieState) {
    join(t, o);
    dispose(t, o, true);
  }
}

void
visitRoots(Thread* t, Heap::Visitor* v)
{
  if (t->state != Thread::ZombieState) {
    t->heapIndex = 0;

    v->visit(&(t->javaThread));
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
  }

  for (Thread* c = t->child; c; c = c->peer) {
    visitRoots(c, v);
  }
}

void
postVisit(Thread* t, Heap::Visitor* v)
{
  Machine* m = t->vm;

  object firstNewTenuredFinalizer = 0;
  object lastNewTenuredFinalizer = 0;

  for (object* p = &(m->finalizers); *p;) {
    v->visit(p);

    if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
      // target is unreachable - queue it up for finalization

      v->visit(&finalizerTarget(t, *p));

      object finalizer = *p;
      *p = finalizerNext(t, finalizer);
      finalizerNext(t, finalizer) = m->finalizeQueue;
      m->finalizeQueue = finalizer;
    } else {
      // target is reachable

      v->visit(&finalizerTarget(t, *p));

      if (m->heap->status(*p) == Heap::Tenured) {
        // the finalizer is tenured, so we remove it from
        // m->finalizers and later add it to m->tenuredFinalizers

        if (lastNewTenuredFinalizer == 0) {
          lastNewTenuredFinalizer = *p;
        }

        object finalizer = *p;
        *p = finalizerNext(t, finalizer);
        finalizerNext(t, finalizer) = firstNewTenuredFinalizer;
        firstNewTenuredFinalizer = finalizer;
      } else {
        p = &finalizerNext(t, *p);
      }
    }
  }

  object firstNewTenuredWeakReference = 0;
  object lastNewTenuredWeakReference = 0;

  for (object* p = &(m->weakReferences); *p;) {
    if (m->heap->status(*p) == Heap::Unreachable) {
      // reference is unreachable - remove it from the list

      fprintf(stderr, "unreachable wr: %p\n", *p);

      *p = jreferenceNext(t, *p);
    } else if (m->heap->status(jreferenceTarget(t, *p)) == Heap::Unreachable) {
      // target is unreachable - clear the reference and remove it
      // from the list

      fprintf(stderr, "target unreachable for wr: %p\n", *p);

      jreferenceTarget(t, *p) = 0;
      *p = jreferenceNext(t, *p);
    } else {
      // both reference and target are reachable

      fprintf(stderr, "viable wr: %p\n", *p);

      v->visit(&jreferenceTarget(t, *p));
      v->visit(p);

      if (m->heap->status(*p) == Heap::Tenured) {
        // the reference is tenured, so we remove it from
        // m->weakReferences and later add it to
        // m->tenuredWeakReferences

        if (lastNewTenuredWeakReference == 0) {
          lastNewTenuredWeakReference = *p;
        }

        object reference = *p;
        *p = jreferenceNext(t, reference);
        jreferenceNext(t, reference) = firstNewTenuredWeakReference;
        firstNewTenuredWeakReference = reference;
      } else {
        p = &jreferenceNext(t, *p);
      }
    }
  }

  if (m->heap->collectionType() == Heap::MajorCollection) {
    for (object* p = &(m->tenuredFinalizers); *p;) {
      v->visit(p);

      if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
        // target is unreachable - queue it up for finalization

        v->visit(&finalizerTarget(t, *p));

        object finalizer = *p;
        *p = finalizerNext(t, finalizer);
        finalizerNext(t, finalizer) = m->finalizeQueue;
        m->finalizeQueue = finalizer;
      } else {
        // target is reachable

        v->visit(&finalizerTarget(t, *p));

        p = &finalizerNext(t, *p);
      }
    }

    for (object* p = &(m->tenuredWeakReferences); *p;) {
      if (m->heap->status(*p) == Heap::Unreachable) {
        // reference is unreachable - remove it from the list

        *p = jreferenceNext(t, *p);
      } else if (m->heap->status(jreferenceTarget(t, *p))
                 == Heap::Unreachable)
      {
        // target is unreachable - clear the reference and remove it
        // from the list

        jreferenceTarget(t, *p) = 0;
        *p = jreferenceNext(t, *p);
      } else {
        // target is reachable

        v->visit(&jreferenceTarget(t, *p));
        v->visit(p);

        p = &jreferenceNext(t, *p);
      }
    }
  }

  if (lastNewTenuredFinalizer) {
    finalizerNext(t, lastNewTenuredFinalizer) = m->tenuredFinalizers;
    m->tenuredFinalizers = lastNewTenuredFinalizer;
  }

  if (lastNewTenuredWeakReference) {
    jreferenceNext(t, lastNewTenuredWeakReference) = m->tenuredWeakReferences;
    m->tenuredWeakReferences = lastNewTenuredWeakReference;
  }
}

void
postCollect(Thread* t)
{
  if (t->large) {
    t->vm->system->free(t->large);
    t->large = 0;
  }

  for (Thread* c = t->child; c; c = c->peer) {
    postCollect(c);
  }
}

void
collect(Thread* t, Heap::CollectionType type)
{
  Machine* m = t->vm;

  class Client: public Heap::Client {
   public:
    Client(Machine* m): m(m) { }

    virtual void visitRoots(Heap::Visitor* v) {
      v->visit(&(m->classMap));
      v->visit(&(m->bootstrapClassMap));
      v->visit(&(m->builtinMap));
      v->visit(&(m->monitorMap));
      v->visit(&(m->types));

      for (Thread* t = m->rootThread; t; t = t->peer) {
        ::visitRoots(t, v);
      }

      postVisit(m->rootThread, v);
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
        cast<uintptr_t>(dst, 0) &= PointerMask;
        cast<uintptr_t>(dst, 0) |= ExtendedMark;
        extendedWord(t, dst, base) = takeHash(t, o);
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

        unsigned fixedSize = classFixedSize(t, class_);
        unsigned arrayElementSize = classArrayElementSize(t, class_);
        unsigned arrayLength
          = (arrayElementSize ?
             cast<uintptr_t>(p, fixedSize - BytesPerWord) : 0);

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

  for (object f = m->finalizeQueue; f; f = finalizerNext(t, f)) {
    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
  }
  m->finalizeQueue = 0;

  killZombies(t, m->rootThread);
}

void
removeMonitor(Thread* t, object o)
{
  abort(t);
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
  tenuredFinalizers(0),
  finalizeQueue(0),
  weakReferences(0),
  tenuredWeakReferences(0),
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

Thread::Thread(Machine* m, Allocator* allocator, object javaThread,
               Thread* parent):
  vtable(&(m->jniEnvVTable)),
  vm(m),
  allocator(allocator),
  parent(parent),
  peer((parent ? parent->child : 0)),
  child(0),
  state(NoState),
  systemThread(0),
  javaThread(javaThread),
  code(0),
  exception(0),
  large(0),
  ip(0),
  sp(0),
  frame(-1),
  heapIndex(0),
  protector(0)
{
  if (parent == 0) {
    assert(this, m->rootThread == 0);
    assert(this, javaThread == 0);

    m->rootThread = this;
    m->unsafe = true;

    if (not m->system->success(m->system->attach(&systemThread))) {
      abort(this);
    }

    Thread* t = this;

#include "type-initializations.cpp"

    object arrayClass = arrayBody(t, t->vm->types, Machine::ArrayType);
    set(t, cast<object>(t->vm->types, 0), arrayClass);

    object classClass = arrayBody(t, m->types, Machine::ClassType);
    set(t, cast<object>(classClass, 0), classClass);

    object intArrayClass = arrayBody(t, m->types, Machine::IntArrayType);
    set(t, cast<object>(intArrayClass, 0), classClass);
    set(t, classSuper(t, intArrayClass),
        arrayBody(t, m->types, Machine::JobjectType));

    m->unsafe = false;

    m->bootstrapClassMap = makeHashMap(this, 0, 0);

#include "type-java-initializations.cpp"

    classVmFlags(t, arrayBody(t, m->types, Machine::WeakReferenceType))
      |= WeakReferenceFlag;

    m->classMap = makeHashMap(this, 0, 0);
    m->builtinMap = makeHashMap(this, 0, 0);
    m->monitorMap = makeHashMap(this, 0, 0);

    builtin::populate(t, m->builtinMap);

    javaThread = makeThread(t, 0, reinterpret_cast<int64_t>(t));
  } else {
    threadPeer(this, javaThread) = reinterpret_cast<jlong>(this);
    parent->child = this;
  }
}

void
Thread::exit()
{
  if (state != Thread::ExitState and
      state != Thread::ZombieState)
  {
    enter(this, Thread::ExclusiveState);

    if (vm->liveCount == 1) {
      vm::exit(this);
    } else {
      enter(this, Thread::ZombieState);
    }
  }
}

void
Thread::dispose()
{
  if (large) {
    vm->system->free(large);
    large = 0;
  }

  if (systemThread) {
    systemThread->dispose();
    systemThread = 0;
  }

  if (allocator) {
    allocator->free(this);
    allocator = 0;
  }
}

void
exit(Thread* t)
{
  enter(t, Thread::ExitState);

  joinAll(t, t->vm->rootThread);

  for (object f = t->vm->finalizers; f; f = finalizerNext(t, f)) {
    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
  }

  disposeAll(t, t->vm->rootThread);
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
      ENTER(t, Thread::IdleState);
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
  if (sizeInBytes > Thread::HeapSizeInBytes and t->large == 0) {
    return allocateLarge(t, sizeInBytes);
  }

  ACQUIRE_RAW(t, t->vm->stateLock);

  while (t->vm->exclusive and t->vm->exclusive != t) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    ENTER(t, Thread::IdleState);
  }

  if (t->heapIndex + divide(sizeInBytes, BytesPerWord)
      >= Thread::HeapSizeInWords)
  {
    ENTER(t, Thread::ExclusiveState);
    collect(t, Heap::MinorCollection);
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

void
stringChars(Thread* t, object string, char* chars)
{
  object data = stringData(t, string);
  if (objectClass(t, data)
      == arrayBody(t, t->vm->types, Machine::ByteArrayType))
  {
    memcpy(chars,
           &byteArrayBody(t, data, stringOffset(t, string)),
           stringLength(t, string));
  } else {
    for (int i = 0; i < stringLength(t, string); ++i) {
      chars[i] = charArrayBody(t, data, stringOffset(t, string) + i);
    }
  }
  chars[stringLength(t, string)] = 0;
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

  t->vm->finalizers = makeFinalizer
    (t, target, reinterpret_cast<void*>(finalize), t->vm->finalizers);
}

System::Monitor*
objectMonitor(Thread* t, object o)
{
  object p = hashMapFind(t, t->vm->monitorMap, o, objectHash, referenceEqual);

  if (p) {
    fprintf(stderr, "found monitor %p for object 0x%x\n",
            static_cast<System::Monitor*>(pointerValue(t, p)),
            objectHash(t, o));

    return static_cast<System::Monitor*>(pointerValue(t, p));
  } else {
    PROTECT(t, o);

    ENTER(t, Thread::ExclusiveState);

    System::Monitor* m;
    System::Status s = t->vm->system->make(&m);
    expect(t, t->vm->system->success(s));

    p = makePointer(t, m);
    PROTECT(t, p);

    object wr = makeWeakReference(t, o, t->vm->weakReferences);
    t->vm->weakReferences = wr;

    fprintf(stderr, "made monitor %p for object 0x%x\n",
            m,
            objectHash(t, o));
    fprintf(stderr, "new wr: %p\n", wr);

    hashMapInsert(t, t->vm->monitorMap, wr, p, referenceHash);

    addFinalizer(t, o, removeMonitor);

    return m;
  }
}

void
noop()
{ }

#include "type-constructors.cpp"

} // namespace vm
