/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/jnienv.h"
#include "avian/machine.h"
#include "avian/util.h"
#include <avian/util/stream.h>
#include "avian/constants.h"
#include "avian/processor.h"
#include "avian/arch.h"
#include "avian/lzma.h"

#include <avian/util/runtime-array.h>
#include <avian/util/math.h>

#if defined(PLATFORM_WINDOWS)
#  define WIN32_LEAN_AND_MEAN
#  include <windows.h>
#endif

using namespace vm;
using namespace avian::util;

namespace {

const bool DebugClassReader = false;

const unsigned NoByte = 0xFFFF;

#ifdef USE_ATOMIC_OPERATIONS
void
atomicIncrement(uint32_t* p, int v)
{
  for (uint32_t old = *p;
       not atomicCompareAndSwap32(p, old, old + v);
       old = *p)
  { }
}
#endif

void
join(Thread* t, Thread* o)
{
  if (t != o) {
    assert(t, o->state != Thread::JoinedState);
    assert(t, (o->flags & Thread::SystemFlag) == 0);
    if (o->flags & Thread::JoinFlag) {
      o->systemThread->join();
    }
    o->state = Thread::JoinedState;
  }
}

#ifndef NDEBUG

bool
find(Thread* t, Thread* o)
{
  return (t == o)
    or (t->peer and find(t->peer, o))
    or (t->child and find(t->child, o));
}

unsigned
count(Thread* t, Thread* o)
{
  unsigned c = 0;

  if (t != o) ++ c;
  if (t->peer) c += count(t->peer, o);
  if (t->child) c += count(t->child, o);

  return c;
}

Thread**
fill(Thread* t, Thread* o, Thread** array)
{
  if (t != o) *(array++) = t;
  if (t->peer) array = fill(t->peer, o, array);
  if (t->child) array = fill(t->child, o, array);

  return array;
}

#endif // not NDEBUG

void
dispose(Thread* t, Thread* o, bool remove)
{
  if (remove) {
#ifndef NDEBUG
    expect(t, find(t->m->rootThread, o));

    unsigned c = count(t->m->rootThread, o);
    THREAD_RUNTIME_ARRAY(t, Thread*, threads, c);
    fill(t->m->rootThread, o, RUNTIME_ARRAY_BODY(threads));
#endif

    if (o->parent) {
      Thread* previous = 0;
      for (Thread* p = o->parent->child; p;) {
        if (p == o) {
          if (p == o->parent->child) {
            o->parent->child = p->peer;
          } else {
            previous->peer = p->peer;
          }
          break;
        } else {
          previous = p;
          p = p->peer;
        }
      }      

      for (Thread* p = o->child; p;) {
        Thread* next = p->peer;
        p->peer = o->parent->child;
        o->parent->child = p;
        p->parent = o->parent;
        p = next;
      }
    } else if (o->child) {
      t->m->rootThread = o->child;

      for (Thread* p = o->peer; p;) {
        Thread* next = p->peer;
        p->peer = t->m->rootThread;
        t->m->rootThread = p;
        p = next;
      }
    } else if (o->peer) {
      t->m->rootThread = o->peer;
    } else {
      abort(t);
    }

#ifndef NDEBUG
    expect(t, not find(t->m->rootThread, o));

    for (unsigned i = 0; i < c; ++i) {
      expect(t, find(t->m->rootThread, RUNTIME_ARRAY_BODY(threads)[i]));
    }
#endif
  }

  o->dispose();
}

void
visitAll(Thread* m, Thread* o, void (*visit)(Thread*, Thread*))
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    visitAll(m, child, visit);
  }

  visit(m, o);
}

void
disposeNoRemove(Thread* m, Thread* o)
{
  dispose(m, o, false);
}

void
interruptDaemon(Thread* m, Thread* o)
{
  if (o->flags & Thread::DaemonFlag) {
    interrupt(m, o);
  }
}

void
turnOffTheLights(Thread* t)
{
  expect(t, t->m->liveCount == 1);

  visitAll(t, t->m->rootThread, join);

  enter(t, Thread::ExitState);

  { object p = 0;
    PROTECT(t, p);

    for (p = t->m->finalizers; p;) {
      object f = p;
      p = finalizerNext(t, p);

      void (*function)(Thread*, object);
      memcpy(&function, &finalizerFinalize(t, f), BytesPerWord);
      if (function) {
        function(t, finalizerTarget(t, f));
      }
    }

    for (p = t->m->tenuredFinalizers; p;) {
      object f = p;
      p = finalizerNext(t, p);

      void (*function)(Thread*, object);
      memcpy(&function, &finalizerFinalize(t, f), BytesPerWord);
      if (function) {
        function(t, finalizerTarget(t, f));
      }
    }
  }

  if (root(t, Machine::VirtualFiles)) {
    for (unsigned i = 0; i < arrayLength(t, root(t, Machine::VirtualFiles));
         ++i)
    {
      object region = arrayBody(t, root(t, Machine::VirtualFiles), i);
      if (region) {
        static_cast<System::Region*>(regionRegion(t, region))->dispose();
      }
    }
  }

  for (object p = root(t, Machine::VirtualFileFinders);
       p; p = finderNext(t, p))
  {
    static_cast<Finder*>(finderFinder(t, p))->dispose();
  }

  Machine* m = t->m;

  visitAll(t, t->m->rootThread, disposeNoRemove);

  System* s = m->system;

  expect(s, m->threadCount == 0);

  Heap* h = m->heap;
  Processor* p = m->processor;
  Classpath* c = m->classpath;
  Finder* bf = m->bootFinder;
  Finder* af = m->appFinder;

  c->dispose();
  h->disposeFixies();
  m->dispose();
  p->dispose();
  bf->dispose();
  af->dispose();
  h->dispose();
  s->dispose();
}

void
killZombies(Thread* t, Thread* o)
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    killZombies(t, child);
  }

  if ((o->flags & Thread::SystemFlag) == 0) {
    switch (o->state) {
    case Thread::ZombieState:
      join(t, o);
      // fall through
      
    case Thread::JoinedState:
      dispose(t, o, true);
      
    default: break;
    }
  }
}

unsigned
footprint(Thread* t)
{
  expect(t, t->criticalLevel == 0);

  unsigned n = t->heapOffset + t->heapIndex + t->backupHeapIndex;

  for (Thread* c = t->child; c; c = c->peer) {
    n += footprint(c);
  }

  return n;
}

void
visitRoots(Thread* t, Heap::Visitor* v)
{
  if (t->state != Thread::ZombieState) {
    v->visit(&(t->javaThread));
    v->visit(&(t->exception));

    t->m->processor->visitObjects(t, v);

    for (Thread::Protector* p = t->protector; p; p = p->next) {
      p->visit(v);
    }
  }

  for (Thread* c = t->child; c; c = c->peer) {
    visitRoots(c, v);
  }
}

bool
walk(Thread*, Heap::Walker* w, uint32_t* mask, unsigned fixedSize,
     unsigned arrayElementSize, unsigned arrayLength, unsigned start)
{
  unsigned fixedSizeInWords = ceilingDivide(fixedSize, BytesPerWord);
  unsigned arrayElementSizeInWords
    = ceilingDivide(arrayElementSize, BytesPerWord);

  for (unsigned i = start; i < fixedSizeInWords; ++i) {
    if (mask[i / 32] & (static_cast<uint32_t>(1) << (i % 32))) {
      if (not w->visit(i)) {
        return false;
      }
    }
  }

  bool arrayObjectElements = false;
  for (unsigned j = 0; j < arrayElementSizeInWords; ++j) {
    unsigned k = fixedSizeInWords + j;
    if (mask[k / 32] & (static_cast<uint32_t>(1) << (k % 32))) {
      arrayObjectElements = true;
      break;
    }
  }

  if (arrayObjectElements) {
    unsigned arrayStart;
    unsigned elementStart;
    if (start > fixedSizeInWords) {
      unsigned s = start - fixedSizeInWords;
      arrayStart = s / arrayElementSizeInWords;
      elementStart = s % arrayElementSizeInWords;
    } else {
      arrayStart = 0;
      elementStart = 0;
    }

    for (unsigned i = arrayStart; i < arrayLength; ++i) {
      for (unsigned j = elementStart; j < arrayElementSizeInWords; ++j) {
        unsigned k = fixedSizeInWords + j;
        if (mask[k / 32] & (static_cast<uint32_t>(1) << (k % 32))) {
          if (not w->visit
              (fixedSizeInWords + (i * arrayElementSizeInWords) + j))
          {
            return false;
          }
        }
      }
    }
  }

  return true;
}

object
findInInterfaces(Thread* t, object class_, object name, object spec,
                 object (*find)(Thread*, object, object, object))
{
  object result = 0;
  if (classInterfaceTable(t, class_)) {
    for (unsigned i = 0;
         i < arrayLength(t, classInterfaceTable(t, class_)) and result == 0;
         i += 2)
    {
      result = find
        (t, arrayBody(t, classInterfaceTable(t, class_), i), name, spec);
    }
  }
  return result;
}

void
finalizerTargetUnreachable(Thread* t, Heap::Visitor* v, object* p)
{
  v->visit(&finalizerTarget(t, *p));

  object finalizer = *p;
  *p = finalizerNext(t, finalizer);

  void (*function)(Thread*, object);
  memcpy(&function, &finalizerFinalize(t, finalizer), BytesPerWord);

  if (function) {
    finalizerNext(t, finalizer) = t->m->finalizeQueue;
    t->m->finalizeQueue = finalizer;
  } else {
    set(t, finalizer, FinalizerQueueTarget, finalizerTarget(t, finalizer));
    set(t, finalizer, FinalizerQueueNext, root(t, Machine::ObjectsToFinalize));
    setRoot(t, Machine::ObjectsToFinalize, finalizer);
  }
}

void
referenceTargetUnreachable(Thread* t, Heap::Visitor* v, object* p)
{
  if (DebugReferences) {
    fprintf(stderr, "target %p unreachable for reference %p\n",
            jreferenceTarget(t, *p), *p);
  }

  v->visit(p);
  jreferenceTarget(t, *p) = 0;

  if (objectClass(t, *p) == type(t, Machine::CleanerType)) {
    object reference = *p;
    *p = jreferenceVmNext(t, reference);

    set(t, reference, CleanerQueueNext, root(t, Machine::ObjectsToClean));
    setRoot(t, Machine::ObjectsToClean, reference);
  } else {
    if (jreferenceQueue(t, *p)
        and t->m->heap->status(jreferenceQueue(t, *p)) != Heap::Unreachable)
    {
      // queue is reachable - add the reference

      v->visit(&jreferenceQueue(t, *p));

      object q = jreferenceQueue(t, *p);

      if (referenceQueueFront(t, q)) {
        set(t, *p, JreferenceJNext, referenceQueueFront(t, q));
      } else {
        set(t, *p, JreferenceJNext, *p);
      }
      set(t, q, ReferenceQueueFront, *p);

      jreferenceQueue(t, *p) = 0;
    }

    *p = jreferenceVmNext(t, *p);
  }
}

void
referenceUnreachable(Thread* t, Heap::Visitor* v, object* p)
{
  object r = static_cast<object>(t->m->heap->follow(*p));

  if (DebugReferences) {
    fprintf(stderr, "reference %p unreachable (target %p)\n",
            *p, jreferenceTarget(t, r));
  }

  if (jreferenceQueue(t, r)
      and t->m->heap->status(jreferenceQueue(t, r)) != Heap::Unreachable)
  {
    // queue is reachable - add the reference
    referenceTargetUnreachable(t, v, p);    
  } else {
    *p = jreferenceVmNext(t, *p);
  }
}

void
referenceTargetReachable(Thread* t, Heap::Visitor* v, object* p)
{
  if (DebugReferences) {
    fprintf(stderr, "target %p reachable for reference %p\n",
            jreferenceTarget(t, *p), *p);
  }

  v->visit(p);
  v->visit(&jreferenceTarget(t, *p));

  if (t->m->heap->status(jreferenceQueue(t, *p)) == Heap::Unreachable) {
    jreferenceQueue(t, *p) = 0;
  } else {
    v->visit(&jreferenceQueue(t, *p));
  }
}

bool
isFinalizable(Thread* t, object o)
{
  return t->m->heap->status(o) == Heap::Unreachable
    and (classVmFlags
         (t, static_cast<object>(t->m->heap->follow(objectClass(t, o))))
         & HasFinalizerFlag);
}

void
clearTargetIfFinalizable(Thread* t, object r)
{
  if (isFinalizable
      (t, static_cast<object>(t->m->heap->follow(jreferenceTarget(t, r)))))
  {
    jreferenceTarget(t, r) = 0;
  }
}

void
postVisit(Thread* t, Heap::Visitor* v)
{
  Machine* m = t->m;
  bool major = m->heap->collectionType() == Heap::MajorCollection;

  assert(t, m->finalizeQueue == 0);

  m->heap->postVisit();

  for (object p = m->weakReferences; p;) {
    object r = static_cast<object>(m->heap->follow(p));
    p = jreferenceVmNext(t, r);
    clearTargetIfFinalizable(t, r);
  }

  if (major) {
    for (object p = m->tenuredWeakReferences; p;) {
      object r = static_cast<object>(m->heap->follow(p));
      p = jreferenceVmNext(t, r);
      clearTargetIfFinalizable(t, r);
    }
  }

  for (Reference* r = m->jniReferences; r; r = r->next) {
    if (r->weak and isFinalizable
        (t, static_cast<object>(t->m->heap->follow(r->target))))
    {
      r->target = 0;
    }
  }

  object firstNewTenuredFinalizer = 0;
  object lastNewTenuredFinalizer = 0;

  { object unreachable = 0;
    for (object* p = &(m->finalizers); *p;) {
      v->visit(p);

      if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
        object finalizer = *p;
        *p = finalizerNext(t, finalizer);

        finalizerNext(t, finalizer) = unreachable;
        unreachable = finalizer;
      } else {
        p = &finalizerNext(t, *p);
      }
    }

    for (object* p = &(m->finalizers); *p;) {
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

    for (object* p = &unreachable; *p;) {
      // target is unreachable - queue it up for finalization
      finalizerTargetUnreachable(t, v, p);
    }
  }

  object firstNewTenuredWeakReference = 0;
  object lastNewTenuredWeakReference = 0;

  for (object* p = &(m->weakReferences); *p;) {
    if (m->heap->status(*p) == Heap::Unreachable) {
      // reference is unreachable
      referenceUnreachable(t, v, p);
    } else if (m->heap->status
               (jreferenceTarget
                (t, static_cast<object>(m->heap->follow(*p))))
               == Heap::Unreachable)
    {
      // target is unreachable
      referenceTargetUnreachable(t, v, p);
    } else {
      // both reference and target are reachable
      referenceTargetReachable(t, v, p);

      if (m->heap->status(*p) == Heap::Tenured) {
        // the reference is tenured, so we remove it from
        // m->weakReferences and later add it to
        // m->tenuredWeakReferences

        if (lastNewTenuredWeakReference == 0) {
          lastNewTenuredWeakReference = *p;
        }

        object reference = *p;
        *p = jreferenceVmNext(t, reference);
        jreferenceVmNext(t, reference) = firstNewTenuredWeakReference;
        firstNewTenuredWeakReference = reference;
      } else {
        p = &jreferenceVmNext(t, *p);
      }
    }
  }

  if (major) {
    { object unreachable = 0;
      for (object* p = &(m->tenuredFinalizers); *p;) {
        v->visit(p);

        if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
          object finalizer = *p;
          *p = finalizerNext(t, finalizer);

          finalizerNext(t, finalizer) = unreachable;
          unreachable = finalizer;
        } else {
          p = &finalizerNext(t, *p);
        }
      }

      for (object* p = &(m->tenuredFinalizers); *p;) {
        // target is reachable
        v->visit(&finalizerTarget(t, *p));
        p = &finalizerNext(t, *p);
      }

      for (object* p = &unreachable; *p;) {
        // target is unreachable - queue it up for finalization
        finalizerTargetUnreachable(t, v, p);
      }
    }

    for (object* p = &(m->tenuredWeakReferences); *p;) {
      if (m->heap->status(*p) == Heap::Unreachable) {
        // reference is unreachable
        referenceUnreachable(t, v, p);
      } else if (m->heap->status
                 (jreferenceTarget
                  (t, static_cast<object>(m->heap->follow(*p))))
                 == Heap::Unreachable)
      {
        // target is unreachable
        referenceTargetUnreachable(t, v, p);
      } else {
        // both reference and target are reachable
        referenceTargetReachable(t, v, p);
        p = &jreferenceVmNext(t, *p);
      }
    }
  }

  if (lastNewTenuredFinalizer) {
    finalizerNext(t, lastNewTenuredFinalizer) = m->tenuredFinalizers;
    m->tenuredFinalizers = firstNewTenuredFinalizer;
  }

  if (lastNewTenuredWeakReference) {
    jreferenceVmNext(t, lastNewTenuredWeakReference)
      = m->tenuredWeakReferences;
    m->tenuredWeakReferences = firstNewTenuredWeakReference;
  }

  for (Reference* r = m->jniReferences; r; r = r->next) {
    if (r->weak) {
      if (m->heap->status(r->target) == Heap::Unreachable) {
        r->target = 0;
      } else {
        v->visit(&(r->target));
      }
    }
  }
}

void
postCollect(Thread* t)
{
#ifdef VM_STRESS
  t->m->heap->free(t->defaultHeap, ThreadHeapSizeInBytes);
  t->defaultHeap = static_cast<uintptr_t*>
    (t->m->heap->allocate(ThreadHeapSizeInBytes));
  memset(t->defaultHeap, 0, ThreadHeapSizeInBytes);
#endif

  if (t->heap == t->defaultHeap) {
    memset(t->defaultHeap, 0, t->heapIndex * BytesPerWord);
  } else {
    memset(t->defaultHeap, 0, ThreadHeapSizeInBytes);
    t->heap = t->defaultHeap;
  }

  t->heapOffset = 0;

  if (t->m->heap->limitExceeded()) {
    // if we're out of memory, pretend the thread-local heap is
    // already full so we don't make things worse:
    t->heapIndex = ThreadHeapSizeInWords;
  } else {
    t->heapIndex = 0;
  }

  if (t->flags & Thread::UseBackupHeapFlag) {
    memset(t->backupHeap, 0, ThreadBackupHeapSizeInBytes);

    t->flags &= ~Thread::UseBackupHeapFlag;
    t->backupHeapIndex = 0;
  }

  for (Thread* c = t->child; c; c = c->peer) {
    postCollect(c);
  }
}

uint64_t
invoke(Thread* t, uintptr_t* arguments)
{
  object m = *reinterpret_cast<object*>(arguments[0]);
  object o = *reinterpret_cast<object*>(arguments[1]);

  t->m->processor->invoke(t, m, o);

  return 1;
}

void
finalizeObject(Thread* t, object o, const char* name)
{
  for (object c = objectClass(t, o); c; c = classSuper(t, c)) {
    for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
      object m = arrayBody(t, classMethodTable(t, c), i);

      if (vm::strcmp(reinterpret_cast<const int8_t*>(name),
                     &byteArrayBody(t, methodName(t, m), 0)) == 0
          and vm::strcmp(reinterpret_cast<const int8_t*>("()V"),
                         &byteArrayBody(t, methodSpec(t, m), 0)) == 0)
      {
        PROTECT(t, m);
        PROTECT(t, o);

        uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(&m),
                                  reinterpret_cast<uintptr_t>(&o) };

        run(t, invoke, arguments);

        t->exception = 0;
        return;
      }               
    }
  }
  abort(t);
}

unsigned
readByte(AbstractStream& s, unsigned* value)
{
  if (*value == NoByte) {
    return s.read1();
  } else {
    unsigned r = *value;
    *value = NoByte;
    return r;
  }
}

object
parseUtf8NonAscii(Thread* t, AbstractStream& s, object bytesSoFar,
                  unsigned byteCount, unsigned sourceIndex, unsigned byteA,
                  unsigned byteB)
{
  PROTECT(t, bytesSoFar);
  
  unsigned length = byteArrayLength(t, bytesSoFar) - 1;
  object value = makeCharArray(t, length + 1);

  unsigned vi = 0;
  for (; vi < byteCount; ++vi) {
    charArrayBody(t, value, vi) = byteArrayBody(t, bytesSoFar, vi);
  }

  for (unsigned si = sourceIndex; si < length; ++si) {
    unsigned a = readByte(s, &byteA);
    if (a & 0x80) {
      if (a & 0x20) {
	// 3 bytes
	si += 2;
	assert(t, si < length);
        unsigned b = readByte(s, &byteB);
	unsigned c = s.read1();
	charArrayBody(t, value, vi++)
          = ((a & 0xf) << 12) | ((b & 0x3f) << 6) | (c & 0x3f);
      } else {
	// 2 bytes
	++ si;
	assert(t, si < length);
        unsigned b = readByte(s, &byteB);

	if (a == 0xC0 and b == 0x80) {
	  charArrayBody(t, value, vi++) = 0;
	} else {
	  charArrayBody(t, value, vi++) = ((a & 0x1f) << 6) | (b & 0x3f);
	}
      }
    } else {
      charArrayBody(t, value, vi++) = a;
    }
  }

  if (vi < length) {
    PROTECT(t, value);
    
    object v = makeCharArray(t, vi + 1);
    memcpy(&charArrayBody(t, v, 0), &charArrayBody(t, value, 0), vi * 2);
    value = v;
  }
  
  return value;
}

object
parseUtf8(Thread* t, AbstractStream& s, unsigned length)
{
  object value = makeByteArray(t, length + 1);
  unsigned vi = 0;
  for (unsigned si = 0; si < length; ++si) {
    unsigned a = s.read1();
    if (a & 0x80) {
      if (a & 0x20) {
	// 3 bytes
        return parseUtf8NonAscii(t, s, value, vi, si, a, NoByte);
      } else {
	// 2 bytes
	unsigned b = s.read1();

	if (a == 0xC0 and b == 0x80) {
          ++ si;
          assert(t, si < length);
	  byteArrayBody(t, value, vi++) = 0;
	} else {
          return parseUtf8NonAscii(t, s, value, vi, si, a, b);
	}
      }
    } else {
      byteArrayBody(t, value, vi++) = a;
    }
  }

  if (vi < length) {
    PROTECT(t, value);
    
    object v = makeByteArray(t, vi + 1);
    memcpy(&byteArrayBody(t, v, 0), &byteArrayBody(t, value, 0), vi);
    value = v;
  }
  
  return value;
}

object
makeByteArray(Thread* t, Stream& s, unsigned length)
{
  object value = makeByteArray(t, length + 1);
  s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, value, 0)), length);
  return value;
}

void
removeByteArray(Thread* t, object o)
{
  hashMapRemove
    (t, root(t, Machine::ByteArrayMap), o, byteArrayHash, objectEqual);
}

object
internByteArray(Thread* t, object array)
{
  PROTECT(t, array);

  ACQUIRE(t, t->m->referenceLock);

  object n = hashMapFindNode
    (t, root(t, Machine::ByteArrayMap), array, byteArrayHash, byteArrayEqual);
  if (n) {
    return jreferenceTarget(t, tripleFirst(t, n));
  } else {
    hashMapInsert(t, root(t, Machine::ByteArrayMap), array, 0, byteArrayHash);
    addFinalizer(t, array, removeByteArray);
    return array;
  }
}

unsigned
parsePoolEntry(Thread* t, Stream& s, uint32_t* index, object pool, unsigned i)
{
  PROTECT(t, pool);

  s.setPosition(index[i]);

  switch (s.read1()) {
  case CONSTANT_Integer:
  case CONSTANT_Float: {
    uint32_t v = s.read4();
    singletonValue(t, pool, i) = v;

    if(DebugClassReader) {
      fprintf(stderr, "    consts[%d] = int/float 0x%x\n", i, v);
    }
  } return 1;
    
  case CONSTANT_Long:
  case CONSTANT_Double: {
    uint64_t v = s.read8();
    memcpy(&singletonValue(t, pool, i), &v, 8);

    if(DebugClassReader) {
      fprintf(stderr, "    consts[%d] = long/double <todo>\n", i);
    }
  } return 2;

  case CONSTANT_Utf8: {
    if (singletonObject(t, pool, i) == 0) {
      object value = internByteArray(t, makeByteArray(t, s, s.read2()));
      set(t, pool, SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = utf8 %s\n", i, &byteArrayBody(t, value, 0));
      }
    }
  } return 1;

  case CONSTANT_Class: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = makeReference(t, 0, singletonObject(t, pool, si), 0);
      set(t, pool, SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = class <todo>\n", i);
      }
    }
  } return 1;

  case CONSTANT_String: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = parseUtf8(t, singletonObject(t, pool, si));
      value = t->m->classpath->makeString
        (t, value, 0, fieldAtOffset<uintptr_t>(value, BytesPerWord) - 1);
      value = intern(t, value);
      set(t, pool, SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = string <todo>\n", i);
      }
    }
  } return 1;

  case CONSTANT_NameAndType: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned ni = s.read2() - 1;
      unsigned ti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ni);
      parsePoolEntry(t, s, index, pool, ti);
        
      object name = singletonObject(t, pool, ni);
      object type = singletonObject(t, pool, ti);
      object value = makePair(t, name, type);
      set(t, pool, SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = nameAndType %s%s\n", i, &byteArrayBody(t, name, 0), &byteArrayBody(t, type, 0));
      }
    }
  } return 1;

  case CONSTANT_Fieldref:
  case CONSTANT_Methodref:
  case CONSTANT_InterfaceMethodref: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned ci = s.read2() - 1;
      unsigned nti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ci);
      parsePoolEntry(t, s, index, pool, nti);

      object class_ = referenceName(t, singletonObject(t, pool, ci));
      object nameAndType = singletonObject(t, pool, nti);

      object value = makeReference
          (t, class_, pairFirst(t, nameAndType), pairSecond(t, nameAndType));
      set(t, pool, SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = method %s.%s%s\n", i, &byteArrayBody(t, class_, 0), &byteArrayBody(t, pairFirst(t, nameAndType), 0), &byteArrayBody(t, pairSecond(t, nameAndType), 0));
      }
    }
  } return 1;

  default: abort(t);
  }
}

object
parsePool(Thread* t, Stream& s)
{
  unsigned count = s.read2() - 1;
  object pool = makeSingletonOfSize(t, count + poolMaskSize(count));
  PROTECT(t, pool);

  if(DebugClassReader) {
    fprintf(stderr, "  const pool entries %d\n", count);
  }

  if (count) {
    uint32_t* index = static_cast<uint32_t*>(t->m->heap->allocate(count * 4));

    THREAD_RESOURCE2(t, uint32_t*, index, unsigned, count,
                     t->m->heap->free(index, count * 4));

    for (unsigned i = 0; i < count; ++i) {
      index[i] = s.position();

      switch (s.read1()) {
      case CONSTANT_Class:
      case CONSTANT_String:
        singletonMarkObject(t, pool, i);
        s.skip(2);
        break;

      case CONSTANT_Integer:
        s.skip(4);
        break;

      case CONSTANT_Float:
        singletonSetBit(t, pool, count, i);
        s.skip(4);
        break;

      case CONSTANT_NameAndType:
      case CONSTANT_Fieldref:
      case CONSTANT_Methodref:
      case CONSTANT_InterfaceMethodref:
        singletonMarkObject(t, pool, i);
        s.skip(4);
        break;

      case CONSTANT_Long:
        s.skip(8);
        ++ i;
        break;

      case CONSTANT_Double:
        singletonSetBit(t, pool, count, i);
        singletonSetBit(t, pool, count, i + 1);
        s.skip(8);
        ++ i;
        break;

      case CONSTANT_Utf8:
        singletonMarkObject(t, pool, i);
        s.skip(s.read2());
        break;

      default: abort(t);
      }
    }

    unsigned end = s.position();

    for (unsigned i = 0; i < count;) {
      i += parsePoolEntry(t, s, index, pool, i);
    }

    s.setPosition(end);
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
      hashMapInsertMaybe(t, map, name, interface, byteArrayHash,
                         byteArrayEqual);
    }
  }
}

object
getClassAddendum(Thread* t, object class_, object pool)
{
  object addendum = classAddendum(t, class_);
  if (addendum == 0) {
    PROTECT(t, class_);

    addendum = makeClassAddendum(t, pool, 0, 0, 0, 0, 0, 0, 0);
    set(t, class_, ClassAddendum, addendum);
  }
  return addendum;
}

void
parseInterfaceTable(Thread* t, Stream& s, object class_, object pool,
                    Machine::Type throwType)
{
  PROTECT(t, class_);
  PROTECT(t, pool);
  
  object map = makeHashMap(t, 0, 0);
  PROTECT(t, map);

  if (classSuper(t, class_)) {
    addInterfaces(t, classSuper(t, class_), map);
  }

  unsigned count = s.read2();
  object table = 0;
  PROTECT(t, table);

  if (count) {
    table = makeArray(t, count);

    object addendum = getClassAddendum(t, class_, pool);
    set(t, addendum, ClassAddendumInterfaceTable, table);
  }

  for (unsigned i = 0; i < count; ++i) {
    object name = referenceName(t, singletonObject(t, pool, s.read2() - 1));
    PROTECT(t, name);

    object interface = resolveClass
      (t, classLoader(t, class_), name, true, throwType);

    PROTECT(t, interface);

    set(t, table, ArrayBody + (i * BytesPerWord), interface);

    hashMapInsertMaybe(t, map, name, interface, byteArrayHash, byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  object interfaceTable = 0;
  if (hashMapSize(t, map)) {
    unsigned length = hashMapSize(t, map);
    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = makeArray(t, length);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    for (HashMapIterator it(t, map); it.hasMore();) {
      object interface = tripleSecond(t, it.next());

      set(t, interfaceTable, ArrayBody + (i * BytesPerWord), interface);
      ++ i;

      if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
        if (classVirtualTable(t, interface)) {
          // we'll fill in this table in parseMethodTable():
          object vtable = makeArray
            (t, arrayLength(t, classVirtualTable(t, interface)));
          
          set(t, interfaceTable, ArrayBody + (i * BytesPerWord), vtable);
        }

        ++i;
      }
    }
  }

  set(t, class_, ClassInterfaceTable, interfaceTable);
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
    unsigned staticOffset = BytesPerWord * 3;
    unsigned staticCount = 0;
  
    object fieldTable = makeArray(t, count);
    PROTECT(t, fieldTable);

    object staticValueTable = makeIntArray(t, count);
    PROTECT(t, staticValueTable);

    object addendum = 0;
    PROTECT(t, addendum);

    THREAD_RUNTIME_ARRAY(t, uint8_t, staticTypes, count);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      unsigned value = 0;

      addendum = 0;

      unsigned code = fieldCode
        (t, byteArrayBody(t, singletonObject(t, pool, spec - 1), 0));

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object name = singletonObject(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (vm::strcmp(reinterpret_cast<const int8_t*>("ConstantValue"),
                       &byteArrayBody(t, name, 0)) == 0)
        {
          value = s.read2();
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                              &byteArrayBody(t, name, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeFieldAddendum(t, pool, 0, 0);
          }
      
          set(t, addendum, AddendumSignature,
              singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("RuntimeVisibleAnnotations"),
                              &byteArrayBody(t, name, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeFieldAddendum(t, pool, 0, 0);
          }

          object body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, AddendumAnnotationTable, body);
        } else {
          s.skip(length);
        }
      }

      object field = makeField
        (t,
         0, // vm flags
         code,
         flags,
         0, // offset
         0, // native ID
         singletonObject(t, pool, name - 1),
         singletonObject(t, pool, spec - 1),
         addendum,
         class_);

      unsigned size = fieldSize(t, code);
      if (flags & ACC_STATIC) {
        while (staticOffset % size) {
          ++ staticOffset;
        }

        fieldOffset(t, field) = staticOffset;

        staticOffset += size;

        intArrayBody(t, staticValueTable, staticCount) = value;

        RUNTIME_ARRAY_BODY(staticTypes)[staticCount++] = code;
      } else {
        if (flags & ACC_FINAL) {
          classVmFlags(t, class_) |= HasFinalMemberFlag;
        }

        while (memberOffset % size) {
          ++ memberOffset;
        }

        fieldOffset(t, field) = memberOffset;

        memberOffset += size;
      }

      set(t, fieldTable, ArrayBody + (i * BytesPerWord), field);
    }

    set(t, class_, ClassFieldTable, fieldTable);

    if (staticCount) {
      unsigned footprint = ceilingDivide(staticOffset - (BytesPerWord * 2),
                                   BytesPerWord);
      object staticTable = makeSingletonOfSize(t, footprint);

      uint8_t* body = reinterpret_cast<uint8_t*>
        (&singletonBody(t, staticTable, 0));

      memcpy(body, &class_, BytesPerWord);
      singletonMarkObject(t, staticTable, 0);

      for (unsigned i = 0, offset = BytesPerWord; i < staticCount; ++i) {
        unsigned size = fieldSize(t, RUNTIME_ARRAY_BODY(staticTypes)[i]);
        while (offset % size) {
          ++ offset;
        }

        unsigned value = intArrayBody(t, staticValueTable, i);
        if (value) {
          switch (RUNTIME_ARRAY_BODY(staticTypes)[i]) {
          case ByteField:
          case BooleanField:
            body[offset] = singletonValue(t, pool, value - 1);
            break;

          case CharField:
          case ShortField:
            *reinterpret_cast<uint16_t*>(body + offset)
              = singletonValue(t, pool, value - 1);
            break;

          case IntField:
          case FloatField:
            *reinterpret_cast<uint32_t*>(body + offset)
              = singletonValue(t, pool, value - 1);
            break;

          case LongField:
          case DoubleField:
            memcpy(body + offset, &singletonValue(t, pool, value - 1), 8);
            break;

          case ObjectField:
            memcpy(body + offset,
                   &singletonObject(t, pool, value - 1),
                   BytesPerWord);
            break;

          default: abort(t);
          }
        }

        if (RUNTIME_ARRAY_BODY(staticTypes)[i] == ObjectField) {
          singletonMarkObject(t, staticTable, offset / BytesPerWord);
        }

        offset += size;
      }

      set(t, class_, ClassStaticTable, staticTable);
    }
  }

  classFixedSize(t, class_) = pad(memberOffset);
  
  if (classSuper(t, class_)
      and memberOffset == classFixedSize(t, classSuper(t, class_)))
  {
    set(t, class_, ClassObjectMask,
        classObjectMask(t, classSuper(t, class_)));
  } else {
    object mask = makeIntArray
      (t, ceilingDivide(classFixedSize(t, class_), 32 * BytesPerWord));
    intArrayBody(t, mask, 0) = 1;

    object superMask = 0;
    if (classSuper(t, class_)) {
      superMask = classObjectMask(t, classSuper(t, class_));
      if (superMask) {
        memcpy(&intArrayBody(t, mask, 0),
               &intArrayBody(t, superMask, 0),
               ceilingDivide(classFixedSize(t, classSuper(t, class_)),
                       32 * BytesPerWord)
               * 4);
      }
    }

    bool sawReferenceField = false;
    object fieldTable = classFieldTable(t, class_);
    if (fieldTable) {
      for (int i = arrayLength(t, fieldTable) - 1; i >= 0; --i) {
        object field = arrayBody(t, fieldTable, i);
        if ((fieldFlags(t, field) & ACC_STATIC) == 0
            and fieldCode(t, field) == ObjectField)
        {
          unsigned index = fieldOffset(t, field) / BytesPerWord;
          intArrayBody(t, mask, (index / 32)) |= 1 << (index % 32);
          sawReferenceField = true;
        }
      }
    }

    if (superMask or sawReferenceField) {
      set(t, class_, ClassObjectMask, mask);
    }
  }
}

uint16_t read16(uint8_t* code, unsigned& ip) {
  uint16_t a = code[ip++];
  uint16_t b = code[ip++];
  return (a << 8) | b;
}

uint32_t read32(uint8_t* code, unsigned& ip) {
  uint32_t b = code[ip++];
  uint32_t a = code[ip++];
  uint32_t c = code[ip++];
  uint32_t d = code[ip++];
  return (a << 24) | (b << 16) | (c << 8) | d;
}

void
disassembleCode(const char* prefix, uint8_t* code, unsigned length)
{
  unsigned ip = 0;

  while(ip < length) {
    unsigned instr;
    fprintf(stderr, "%s%x:\t", prefix, ip);
    switch (instr = code[ip++]) {
      case aaload: fprintf(stderr, "aaload\n"); break;
      case aastore: fprintf(stderr, "aastore\n"); break;

      case aconst_null: fprintf(stderr, "aconst_null\n"); break;

      case aload: fprintf(stderr, "aload %02x\n", code[ip++]); break;
      case aload_0: fprintf(stderr, "aload_0\n"); break;
      case aload_1: fprintf(stderr, "aload_1\n"); break;
      case aload_2: fprintf(stderr, "aload_2\n"); break;
      case aload_3: fprintf(stderr, "aload_3\n"); break;

      case anewarray: fprintf(stderr, "anewarray %04x\n", read16(code, ip)); break;
      case areturn: fprintf(stderr, "areturn\n"); break;
      case arraylength: fprintf(stderr, "arraylength\n"); break;

      case astore: fprintf(stderr, "astore %02x\n", code[ip++]); break;
      case astore_0: fprintf(stderr, "astore_0\n"); break;
      case astore_1: fprintf(stderr, "astore_1\n"); break;
      case astore_2: fprintf(stderr, "astore_2\n"); break;
      case astore_3: fprintf(stderr, "astore_3\n"); break;


      case athrow: fprintf(stderr, "athrow\n"); break;
      case baload: fprintf(stderr, "baload\n"); break;
      case bastore: fprintf(stderr, "bastore\n"); break;

      case bipush: fprintf(stderr, "bipush %02x\n", code[ip++]); break;
      case caload: fprintf(stderr, "caload\n"); break;
      case castore: fprintf(stderr, "castore\n"); break;
      case checkcast: fprintf(stderr, "checkcast %04x\n", read16(code, ip)); break;
      case d2f: fprintf(stderr, "d2f\n"); break;
      case d2i: fprintf(stderr, "d2i\n"); break;
      case d2l: fprintf(stderr, "d2l\n"); break;
      case dadd: fprintf(stderr, "dadd\n"); break;
      case daload: fprintf(stderr, "daload\n"); break;
      case dastore: fprintf(stderr, "dastore\n"); break;
      case dcmpg: fprintf(stderr, "dcmpg\n"); break;
      case dcmpl: fprintf(stderr, "dcmpl\n"); break;
      case dconst_0: fprintf(stderr, "dconst_0\n"); break;
      case dconst_1: fprintf(stderr, "dconst_1\n"); break;
      case ddiv: fprintf(stderr, "ddiv\n"); break;
      case dmul: fprintf(stderr, "dmul\n"); break;
      case dneg: fprintf(stderr, "dneg\n"); break;
      case vm::drem: fprintf(stderr, "drem\n"); break;
      case dsub: fprintf(stderr, "dsub\n"); break;
      case dup: fprintf(stderr, "dup\n"); break;
      case dup_x1: fprintf(stderr, "dup_x1\n"); break;
      case dup_x2: fprintf(stderr, "dup_x2\n"); break;
      case dup2: fprintf(stderr, "dup2\n"); break;
      case dup2_x1: fprintf(stderr, "dup2_x1\n"); break;
      case dup2_x2: fprintf(stderr, "dup2_x2\n"); break;
      case f2d: fprintf(stderr, "f2d\n"); break;
      case f2i: fprintf(stderr, "f2i\n"); break;
      case f2l: fprintf(stderr, "f2l\n"); break;
      case fadd: fprintf(stderr, "fadd\n"); break;
      case faload: fprintf(stderr, "faload\n"); break;
      case fastore: fprintf(stderr, "fastore\n"); break;
      case fcmpg: fprintf(stderr, "fcmpg\n"); break;
      case fcmpl: fprintf(stderr, "fcmpl\n"); break;
      case fconst_0: fprintf(stderr, "fconst_0\n"); break;
      case fconst_1: fprintf(stderr, "fconst_1\n"); break;
      case fconst_2: fprintf(stderr, "fconst_2\n"); break;
      case fdiv: fprintf(stderr, "fdiv\n"); break;
      case fmul: fprintf(stderr, "fmul\n"); break;
      case fneg: fprintf(stderr, "fneg\n"); break;
      case frem: fprintf(stderr, "frem\n"); break;
      case fsub: fprintf(stderr, "fsub\n"); break;

      case getfield: fprintf(stderr, "getfield %04x\n", read16(code, ip)); break;
      case getstatic: fprintf(stderr, "getstatic %04x\n", read16(code, ip)); break;
      case goto_: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "goto %04x\n", offset + ip - 3);
      } break;
      case goto_w: {
        int32_t offset = read32(code, ip);
        fprintf(stderr, "goto_w %08x\n", offset + ip - 5);
      } break;

      case i2b: fprintf(stderr, "i2b\n"); break;
      case i2c: fprintf(stderr, "i2c\n"); break;
      case i2d: fprintf(stderr, "i2d\n"); break;
      case i2f: fprintf(stderr, "i2f\n"); break;
      case i2l: fprintf(stderr, "i2l\n"); break;
      case i2s: fprintf(stderr, "i2s\n"); break;
      case iadd: fprintf(stderr, "iadd\n"); break;
      case iaload: fprintf(stderr, "iaload\n"); break;
      case iand: fprintf(stderr, "iand\n"); break;
      case iastore: fprintf(stderr, "iastore\n"); break;
      case iconst_m1: fprintf(stderr, "iconst_m1\n"); break;
      case iconst_0: fprintf(stderr, "iconst_0\n"); break;
      case iconst_1: fprintf(stderr, "iconst_1\n"); break;
      case iconst_2: fprintf(stderr, "iconst_2\n"); break;
      case iconst_3: fprintf(stderr, "iconst_3\n"); break;
      case iconst_4: fprintf(stderr, "iconst_4\n"); break;
      case iconst_5: fprintf(stderr, "iconst_5\n"); break;
      case idiv: fprintf(stderr, "idiv\n"); break;

      case if_acmpeq: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_acmpeq %04x\n", offset + ip - 3);
      } break;
      case if_acmpne: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_acmpne %04x\n", offset + ip - 3);
      } break;
      case if_icmpeq: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmpeq %04x\n", offset + ip - 3);
      } break;
      case if_icmpne: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmpne %04x\n", offset + ip - 3);
      } break;

      case if_icmpgt: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmpgt %04x\n", offset + ip - 3);
      } break;
      case if_icmpge: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmpge %04x\n", offset + ip - 3);
      } break;
      case if_icmplt: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmplt %04x\n", offset + ip - 3);
      } break;
      case if_icmple: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "if_icmple %04x\n", offset + ip - 3);
      } break;

      case ifeq: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifeq %04x\n", offset + ip - 3);
      } break;
      case ifne: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifne %04x\n", offset + ip - 3);
      } break;
      case ifgt: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifgt %04x\n", offset + ip - 3);
      } break;
      case ifge: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifge %04x\n", offset + ip - 3);
      } break;
      case iflt: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "iflt %04x\n", offset + ip - 3);
      } break;
      case ifle: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifle %04x\n", offset + ip - 3);
      } break;

      case ifnonnull: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifnonnull %04x\n", offset + ip - 3);
      } break;
      case ifnull: {
        int16_t offset = read16(code, ip);
        fprintf(stderr, "ifnull %04x\n", offset + ip - 3);
      } break;

      case iinc: {
        uint8_t a = code[ip++];
        uint8_t b = code[ip++];
        fprintf(stderr, "iinc %02x %02x\n", a, b);
      } break;

      case iload: fprintf(stderr, "iload %02x\n", code[ip++]); break;
      case fload: fprintf(stderr, "fload %02x\n", code[ip++]); break;

      case iload_0: fprintf(stderr, "iload_0\n"); break;
      case fload_0: fprintf(stderr, "fload_0\n"); break;
      case iload_1: fprintf(stderr, "iload_1\n"); break;
      case fload_1: fprintf(stderr, "fload_1\n"); break;

      case iload_2: fprintf(stderr, "iload_2\n"); break;
      case fload_2: fprintf(stderr, "fload_2\n"); break;
      case iload_3: fprintf(stderr, "iload_3\n"); break;
      case fload_3: fprintf(stderr, "fload_3\n"); break;

      case imul: fprintf(stderr, "imul\n"); break;
      case ineg: fprintf(stderr, "ineg\n"); break;

      case instanceof: fprintf(stderr, "instanceof %04x\n", read16(code, ip)); break;
      case invokeinterface: fprintf(stderr, "invokeinterface %04x\n", read16(code, ip)); break;
      case invokespecial: fprintf(stderr, "invokespecial %04x\n", read16(code, ip)); break;
      case invokestatic: fprintf(stderr, "invokestatic %04x\n", read16(code, ip)); break;
      case invokevirtual: fprintf(stderr, "invokevirtual %04x\n", read16(code, ip)); break;

      case ior: fprintf(stderr, "ior\n"); break;
      case irem: fprintf(stderr, "irem\n"); break;
      case ireturn: fprintf(stderr, "ireturn\n"); break;
      case freturn: fprintf(stderr, "freturn\n"); break;
      case ishl: fprintf(stderr, "ishl\n"); break;
      case ishr: fprintf(stderr, "ishr\n"); break;

      case istore: fprintf(stderr, "istore %02x\n", code[ip++]); break;
      case fstore: fprintf(stderr, "fstore %02x\n", code[ip++]); break;

      case istore_0: fprintf(stderr, "istore_0\n"); break;
      case fstore_0: fprintf(stderr, "fstore_0\n"); break;
      case istore_1: fprintf(stderr, "istore_1\n"); break;
      case fstore_1: fprintf(stderr, "fstore_1\n"); break;
      case istore_2: fprintf(stderr, "istore_2\n"); break;
      case fstore_2: fprintf(stderr, "fstore_2\n"); break;
      case istore_3: fprintf(stderr, "istore_3\n"); break;
      case fstore_3: fprintf(stderr, "fstore_3\n"); break;

      case isub: fprintf(stderr, "isub\n"); break;
      case iushr: fprintf(stderr, "iushr\n"); break;
      case ixor: fprintf(stderr, "ixor\n"); break;

      case jsr: fprintf(stderr, "jsr %04x\n", read16(code, ip)); break;
      case jsr_w: fprintf(stderr, "jsr_w %08x\n", read32(code, ip)); break;

      case l2d: fprintf(stderr, "l2d\n"); break;
      case l2f: fprintf(stderr, "l2f\n"); break;
      case l2i: fprintf(stderr, "l2i\n"); break;
      case ladd: fprintf(stderr, "ladd\n"); break;
      case laload: fprintf(stderr, "laload\n"); break;

      case land: fprintf(stderr, "land\n"); break;
      case lastore: fprintf(stderr, "lastore\n"); break;

      case lcmp: fprintf(stderr, "lcmp\n"); break;
      case lconst_0: fprintf(stderr, "lconst_0\n"); break;
      case lconst_1: fprintf(stderr, "lconst_1\n"); break;

      case ldc: fprintf(stderr, "ldc %04x\n", read16(code, ip)); break;
      case ldc_w: fprintf(stderr, "ldc_w %08x\n", read32(code, ip)); break;
      case ldc2_w: fprintf(stderr, "ldc2_w %04x\n", read16(code, ip)); break;

      case ldiv_: fprintf(stderr, "ldiv_\n"); break;

      case lload: fprintf(stderr, "lload %02x\n", code[ip++]); break;
      case dload: fprintf(stderr, "dload %02x\n", code[ip++]); break;

      case lload_0: fprintf(stderr, "lload_0\n"); break;
      case dload_0: fprintf(stderr, "dload_0\n"); break;
      case lload_1: fprintf(stderr, "lload_1\n"); break;
      case dload_1: fprintf(stderr, "dload_1\n"); break;
      case lload_2: fprintf(stderr, "lload_2\n"); break;
      case dload_2: fprintf(stderr, "dload_2\n"); break;
      case lload_3: fprintf(stderr, "lload_3\n"); break;
      case dload_3: fprintf(stderr, "dload_3\n"); break;

      case lmul: fprintf(stderr, "lmul\n"); break;
      case lneg: fprintf(stderr, "lneg\n"); break;

      case lookupswitch: {
        int32_t default_ = read32(code, ip);
        int32_t pairCount = read32(code, ip);
        fprintf(stderr, "lookupswitch default: %d pairCount: %d\n", default_, pairCount);

        for (int i = 0; i < pairCount; i++) {
          int32_t k = read32(code, ip);
          int32_t d = read32(code, ip);
          fprintf(stderr, "%s  key: %02x dest: %2x\n", prefix, k, d);
        }
      } break;

      case lor: fprintf(stderr, "lor\n"); break;
      case lrem: fprintf(stderr, "lrem\n"); break;
      case lreturn: fprintf(stderr, "lreturn\n"); break;
      case dreturn: fprintf(stderr, "dreturn\n"); break;
      case lshl: fprintf(stderr, "lshl\n"); break;
      case lshr: fprintf(stderr, "lshr\n"); break;

      case lstore: fprintf(stderr, "lstore %02x\n", code[ip++]); break;
      case dstore: fprintf(stderr, "dstore %02x\n", code[ip++]); break;

      case lstore_0: fprintf(stderr, "lstore_0\n"); break;
      case dstore_0: fprintf(stderr, "dstore_0\n"); break;
      case lstore_1: fprintf(stderr, "lstore_1\n"); break;
      case dstore_1: fprintf(stderr, "dstore_1\n"); break;
      case lstore_2: fprintf(stderr, "lstore_2\n"); break;
      case dstore_2: fprintf(stderr, "dstore_2\n"); break;
      case lstore_3: fprintf(stderr, "lstore_3\n"); break;
      case dstore_3: fprintf(stderr, "dstore_3\n"); break;

      case lsub: fprintf(stderr, "lsub\n"); break;
      case lushr: fprintf(stderr, "lushr\n"); break;
      case lxor: fprintf(stderr, "lxor\n"); break;

      case monitorenter: fprintf(stderr, "monitorenter\n"); break;
      case monitorexit: fprintf(stderr, "monitorexit\n"); break;

      case multianewarray: {
        unsigned type = read16(code, ip);
        fprintf(stderr, "multianewarray %04x %02x\n", type, code[ip++]);
      } break;

      case new_: fprintf(stderr, "new %04x\n", read16(code, ip)); break;

      case newarray: fprintf(stderr, "newarray %02x\n", code[ip++]); break;

      case nop: fprintf(stderr, "nop\n"); break;
      case pop_: fprintf(stderr, "pop\n"); break;
      case pop2: fprintf(stderr, "pop2\n"); break;

      case putfield: fprintf(stderr, "putfield %04x\n", read16(code, ip)); break;
      case putstatic: fprintf(stderr, "putstatic %04x\n", read16(code, ip)); break;

      case ret: fprintf(stderr, "ret %02x\n", code[ip++]); break;

      case return_: fprintf(stderr, "return_\n"); break;
      case saload: fprintf(stderr, "saload\n"); break;
      case sastore: fprintf(stderr, "sastore\n"); break;

      case sipush: fprintf(stderr, "sipush %04x\n", read16(code, ip)); break;

      case swap: fprintf(stderr, "swap\n"); break;

      case tableswitch: {
        int32_t default_ = read32(code, ip);
        int32_t bottom = read32(code, ip);
        int32_t top = read32(code, ip);
        fprintf(stderr, "tableswitch default: %d bottom: %d top: %d\n", default_, bottom, top);

        for (int i = 0; i < top - bottom + 1; i++) {
          int32_t d = read32(code, ip);
          fprintf(stderr, "%s  key: %d dest: %2x\n", prefix, i + bottom, d);
        }
      } break;

      case wide: {
        switch (code[ip++]) {
          case aload: fprintf(stderr, "wide aload %04x\n", read16(code, ip)); break;

          case astore: fprintf(stderr, "wide astore %04x\n", read16(code, ip)); break;
          case iinc: fprintf(stderr, "wide iinc %04x %04x\n", read16(code, ip), read16(code, ip)); break;
          case iload: fprintf(stderr, "wide iload %04x\n", read16(code, ip)); break;
          case istore: fprintf(stderr, "wide istore %04x\n", read16(code, ip)); break;
          case lload: fprintf(stderr, "wide lload %04x\n", read16(code, ip)); break;
          case lstore: fprintf(stderr, "wide lstore %04x\n", read16(code, ip)); break;
          case ret: fprintf(stderr, "wide ret %04x\n", read16(code, ip)); break;

          default: {
            fprintf(stderr, "unknown wide instruction %02x %04x\n", instr, read16(code, ip));
          }
        }
      } break;

      default: {
        fprintf(stderr, "unknown instruction %02x\n", instr);
      }
    }
  }
}

object
parseCode(Thread* t, Stream& s, object pool)
{
  PROTECT(t, pool);

  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  if(DebugClassReader) {
    fprintf(stderr, "    code: maxStack %d maxLocals %d length %d\n", maxStack, maxLocals, length);
  }

  object code = makeCode(t, pool, 0, 0, 0, 0, maxStack, maxLocals, length);
  s.read(&codeBody(t, code, 0), length);
  PROTECT(t, code);

  if(DebugClassReader) {
    disassembleCode("      ", &codeBody(t, code, 0), length);
  }

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    object eht = makeExceptionHandlerTable(t, ehtLength);
    for (unsigned i = 0; i < ehtLength; ++i) {
      unsigned start = s.read2();
      unsigned end = s.read2();
      unsigned ip = s.read2();
      unsigned catchType = s.read2();
      exceptionHandlerTableBody(t, eht, i) = exceptionHandler
        (start, end, ip, catchType);
    }

    set(t, code, CodeExceptionHandlerTable, eht);
  }

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    object name = singletonObject(t, pool, s.read2() - 1);
    unsigned length = s.read4();

    if (vm::strcmp(reinterpret_cast<const int8_t*>("LineNumberTable"),
                   &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned lntLength = s.read2();
      object lnt = makeLineNumberTable(t, lntLength);
      for (unsigned i = 0; i < lntLength; ++i) {
        unsigned ip = s.read2();
        unsigned line = s.read2();
        lineNumberTableBody(t, lnt, i) = lineNumber(ip, line);
      }

      set(t, code, CodeLineNumberTable, lnt);
    } else {
      s.skip(length);
    }
  }

  return code;
}

object
addInterfaceMethods(Thread* t, object class_, object virtualMap,
                    unsigned* virtualCount, bool makeList)
{
  object itable = classInterfaceTable(t, class_);
  if (itable) {
    PROTECT(t, class_);
    PROTECT(t, virtualMap);  
    PROTECT(t, itable);
    
    object list = 0;
    PROTECT(t, list);

    object method = 0;
    PROTECT(t, method);

    object vtable = 0;
    PROTECT(t, vtable);

   unsigned stride = (classFlags(t, class_) & ACC_INTERFACE) ? 1 : 2;
   for (unsigned i = 0; i < arrayLength(t, itable); i += stride) {
      vtable = classVirtualTable(t, arrayBody(t, itable, i));
      if (vtable) {
        for (unsigned j = 0; j < arrayLength(t, vtable); ++j) {
          method = arrayBody(t, vtable, j);
          object n = hashMapFindNode
            (t, virtualMap, method, methodHash, methodEqual);
          if (n == 0) {
            method = makeMethod
              (t,
               methodVmFlags(t, method),
               methodReturnCode(t, method),
               methodParameterCount(t, method),
               methodParameterFootprint(t, method),
               methodFlags(t, method),
               (*virtualCount)++,
               0,
               0,
               methodName(t, method),
               methodSpec(t, method),
               0,
               class_,
               0);

            hashMapInsert(t, virtualMap, method, method, methodHash);

            if (makeList) {
              if (list == 0) {
                list = vm::makeList(t, 0, 0, 0);
              }
              listAppend(t, list, method);
            }
          }
        }
      }
    }

    return list;
  }

  return 0;
}

void
parseMethodTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  object virtualMap = makeHashMap(t, 0, 0);
  PROTECT(t, virtualMap);

  unsigned virtualCount = 0;
  unsigned declaredVirtualCount = 0;

  object superVirtualTable = 0;
  PROTECT(t, superVirtualTable);

  if (classFlags(t, class_) & ACC_INTERFACE) {
    addInterfaceMethods(t, class_, virtualMap, &virtualCount, false);
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

  if(DebugClassReader) {
    fprintf(stderr, "  method count %d\n", count);
  }

  if (count) {
    object methodTable = makeArray(t, count);
    PROTECT(t, methodTable);

    object addendum = 0;
    PROTECT(t, addendum);

    object code = 0;
    PROTECT(t, code);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      if(DebugClassReader) {
        fprintf(stderr, "    method flags %d name %d spec %d '%s%s'\n", flags, name, spec, 
          &byteArrayBody(t, singletonObject(t, pool, name - 1), 0),
          &byteArrayBody(t, singletonObject(t, pool, spec - 1), 0));
      }

      addendum = 0;
      code = 0;

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object attributeName = singletonObject(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (vm::strcmp(reinterpret_cast<const int8_t*>("Code"),
                       &byteArrayBody(t, attributeName, 0)) == 0)
        {
          code = parseCode(t, s, pool);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Exceptions"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0);
          }
          unsigned exceptionCount = s.read2();
          object body = makeShortArray(t, exceptionCount);
          for (unsigned i = 0; i < exceptionCount; ++i) {
            shortArrayBody(t, body, i) = s.read2();
          }
          set(t, addendum, MethodAddendumExceptionTable, body);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("AnnotationDefault"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0);
          }

          object body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, MethodAddendumAnnotationDefault, body);          
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0);
          }
      
          set(t, addendum, AddendumSignature,
              singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("RuntimeVisibleAnnotations"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0);
          }

          object body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, AddendumAnnotationTable, body);
        } else {
          s.skip(length);
        }
      }

      const char* specString = reinterpret_cast<const char*>
        (&byteArrayBody(t, singletonObject(t, pool, spec - 1), 0));

      unsigned parameterCount;
      unsigned returnCode;
      scanMethodSpec(t, specString, &parameterCount, &returnCode);

      object method =  t->m->processor->makeMethod
        (t,
         0, // vm flags
         returnCode,
         parameterCount,
         parameterFootprint(t, specString, flags & ACC_STATIC),
         flags,
         0, // offset
         singletonObject(t, pool, name - 1),
         singletonObject(t, pool, spec - 1),
         addendum,
         class_,
         code);

      PROTECT(t, method);

      if (methodVirtual(t, method)) {
        ++ declaredVirtualCount;

        object p = hashMapFindNode
          (t, virtualMap, method, methodHash, methodEqual);

        if (p) {
          methodOffset(t, method) = methodOffset(t, tripleFirst(t, p));

          set(t, p, TripleSecond, method);
        } else {
          methodOffset(t, method) = virtualCount++;

          listAppend(t, newVirtuals, method);

          hashMapInsert(t, virtualMap, method, method, methodHash);
        }

        if (UNLIKELY((classFlags(t, class_) & ACC_INTERFACE) == 0
                     and vm::strcmp
                     (reinterpret_cast<const int8_t*>("finalize"), 
                      &byteArrayBody(t, methodName(t, method), 0)) == 0
                     and vm::strcmp
                     (reinterpret_cast<const int8_t*>("()V"),
                      &byteArrayBody(t, methodSpec(t, method), 0)) == 0
                     and (not emptyMethod(t, method))))
        {
          classVmFlags(t, class_) |= HasFinalizerFlag;
        }
      } else {
        methodOffset(t, method) = i;

        if (vm::strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                       &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          methodVmFlags(t, method) |= ClassInitFlag;
          classVmFlags(t, class_) |= NeedInitFlag;
        } else if (vm::strcmp
                   (reinterpret_cast<const int8_t*>("<init>"), 
                    &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          methodVmFlags(t, method) |= ConstructorFlag;
        }
      }

      set(t, methodTable, ArrayBody + (i * BytesPerWord), method);
    }

    set(t, class_, ClassMethodTable, methodTable);
  }

  object abstractVirtuals;
  if (classFlags(t, class_) & ACC_INTERFACE) {
    abstractVirtuals = 0;
  } else {
    abstractVirtuals = addInterfaceMethods
      (t, class_, virtualMap, &virtualCount, true);
  }
  PROTECT(t, abstractVirtuals);

  bool populateInterfaceVtables = false;

  if (declaredVirtualCount == 0
      and abstractVirtuals == 0
      and (classFlags(t, class_) & ACC_INTERFACE) == 0)
  {
    if (classSuper(t, class_)) {
      // inherit virtual table from superclass
      set(t, class_, ClassVirtualTable, superVirtualTable);
      
      if (classInterfaceTable(t, classSuper(t, class_))
          and arrayLength(t, classInterfaceTable(t, class_))
          == arrayLength
          (t, classInterfaceTable(t, classSuper(t, class_))))
      {
        // inherit interface table from superclass
        set(t, class_, ClassInterfaceTable,
            classInterfaceTable(t, classSuper(t, class_)));
      } else {
        populateInterfaceVtables = true;
      }
    } else {
      // apparently, Object does not have any virtual methods.  We
      // give it a vtable anyway so code doesn't break elsewhere.
      object vtable = makeArray(t, 0);
      set(t, class_, ClassVirtualTable, vtable);
    }
  } else if (virtualCount) {
    // generate class vtable

    object vtable = makeArray(t, virtualCount);

    unsigned i = 0;
    if (classFlags(t, class_) & ACC_INTERFACE) {
      PROTECT(t, vtable);

      for (HashMapIterator it(t, virtualMap); it.hasMore();) {
        object method = tripleFirst(t, it.next());
        assert(t, arrayBody(t, vtable, methodOffset(t, method)) == 0);
        set(t, vtable, ArrayBody + (methodOffset(t, method) * BytesPerWord),
            method);
        ++ i;
      }
    } else {
      populateInterfaceVtables = true;

      if (superVirtualTable) {
        for (; i < arrayLength(t, superVirtualTable); ++i) {
          object method = arrayBody(t, superVirtualTable, i);
          method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);

          set(t, vtable, ArrayBody + (i * BytesPerWord), method);
        }
      }

      for (object p = listFront(t, newVirtuals); p; p = pairSecond(t, p)) {
        set(t, vtable, ArrayBody + (i * BytesPerWord), pairFirst(t, p));
        ++ i;
      }

      if (abstractVirtuals) {
        PROTECT(t, vtable);

        object addendum = getClassAddendum(t, class_, pool);
        set(t, addendum, ClassAddendumMethodTable,
            classMethodTable(t, class_));

        unsigned oldLength = classMethodTable(t, class_) ?
          arrayLength(t, classMethodTable(t, class_)) : 0;

        object newMethodTable = makeArray
          (t, oldLength + listSize(t, abstractVirtuals));

        if (oldLength) {
          memcpy(&arrayBody(t, newMethodTable, 0),
                 &arrayBody(t, classMethodTable(t, class_), 0),
                 oldLength * sizeof(object));
        }

        mark(t, newMethodTable, ArrayBody, oldLength);

        unsigned mti = oldLength;
        for (object p = listFront(t, abstractVirtuals);
             p; p = pairSecond(t, p))
        {
          set(t, newMethodTable,
              ArrayBody + ((mti++) * BytesPerWord), pairFirst(t, p));

          set(t, vtable,
              ArrayBody + ((i++) * BytesPerWord), pairFirst(t, p));
        }

        assert(t, arrayLength(t, newMethodTable) == mti);

        set(t, class_, ClassMethodTable, newMethodTable);
      }
    }

    assert(t, arrayLength(t, vtable) == i);

    set(t, class_, ClassVirtualTable, vtable);
  }

  if (populateInterfaceVtables) {
    // generate interface vtables
    object itable = classInterfaceTable(t, class_);
    if (itable) {
      PROTECT(t, itable);

      for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
        object ivtable = classVirtualTable(t, arrayBody(t, itable, i));
        if (ivtable) {
          object vtable = arrayBody(t, itable, i + 1);
        
          for (unsigned j = 0; j < arrayLength(t, ivtable); ++j) {
            object method = arrayBody(t, ivtable, j);
            method = hashMapFind
              (t, virtualMap, method, methodHash, methodEqual);
            assert(t, method);
              
            set(t, vtable, ArrayBody + (j * BytesPerWord), method);
          }
        }
      }
    }
  }
}

void
parseAttributeTable(Thread* t, Stream& s, object class_, object pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    object name = singletonObject(t, pool, s.read2() - 1);
    unsigned length = s.read4();

    if (vm::strcmp(reinterpret_cast<const int8_t*>("SourceFile"),
                   &byteArrayBody(t, name, 0)) == 0)
    {
      set(t, class_, ClassSourceFile, singletonObject(t, pool, s.read2() - 1));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      object addendum = getClassAddendum(t, class_, pool);
      set(t, addendum, AddendumSignature,
          singletonObject(t, pool, s.read2() - 1));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("InnerClasses"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned innerClassCount = s.read2();
      object table = makeArray(t, innerClassCount);
      PROTECT(t, table);

      for (unsigned i = 0; i < innerClassCount; ++i) {
        int16_t inner = s.read2();
        int16_t outer = s.read2();
        int16_t name = s.read2();
        int16_t flags = s.read2();

        object reference = makeInnerClassReference
          (t,
           inner ? referenceName(t, singletonObject(t, pool, inner - 1)) : 0,
           outer ? referenceName(t, singletonObject(t, pool, outer - 1)) : 0,
           name ? singletonObject(t, pool, name - 1) : 0,
           flags);

        set(t, table, ArrayBody + (i * BytesPerWord), reference);

        if (0 == strcmp
            (&byteArrayBody(t, className(t, class_), 0),
             &byteArrayBody(t, innerClassReferenceInner(t, reference), 0)))
        {
          classFlags(t, class_) = flags;
        }
      }

      object addendum = getClassAddendum(t, class_, pool);
      set(t, addendum, ClassAddendumInnerClassTable, table);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                          ("RuntimeVisibleAnnotations"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      object body = makeByteArray(t, length);
      PROTECT(t, body);
      s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)), length);

      object addendum = getClassAddendum(t, class_, pool);
      set(t, addendum, AddendumAnnotationTable, body);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                          ("EnclosingMethod"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      int16_t enclosingClass = s.read2();
      int16_t enclosingMethod = s.read2();

      object addendum = getClassAddendum(t, class_, pool);

      set(t, addendum, ClassAddendumEnclosingClass,
          referenceName(t, singletonObject(t, pool, enclosingClass - 1)));

      set(t, addendum, ClassAddendumEnclosingMethod, enclosingMethod
          ? singletonObject(t, pool, enclosingMethod - 1) : 0);
    } else {
      s.skip(length);
    }
  }
}

void
updateClassTables(Thread* t, object newClass, object oldClass)
{
  object fieldTable = classFieldTable(t, newClass);
  if (fieldTable) {
    for (unsigned i = 0; i < arrayLength(t, fieldTable); ++i) {
      set(t, arrayBody(t, fieldTable, i), FieldClass, newClass);
    }
  }

  object staticTable = classStaticTable(t, newClass);
  if (staticTable) {
    set(t, staticTable, SingletonBody, newClass);
  }

  if (classFlags(t, newClass) & ACC_INTERFACE) {
    object virtualTable = classVirtualTable(t, newClass);
    if (virtualTable) {
      for (unsigned i = 0; i < arrayLength(t, virtualTable); ++i) {
        if (methodClass(t, arrayBody(t, virtualTable, i)) == oldClass) {
          set(t, arrayBody(t, virtualTable, i), MethodClass, newClass);
        }
      }
    }
  }

  object methodTable = classMethodTable(t, newClass);
  if (methodTable) {
    for (unsigned i = 0; i < arrayLength(t, methodTable); ++i) {
      set(t, arrayBody(t, methodTable, i), MethodClass, newClass);
    }
  }
}

void
updateBootstrapClass(Thread* t, object bootstrapClass, object class_)
{
  expect(t, bootstrapClass != class_);

  // verify that the classes have the same layout
  expect(t, classSuper(t, bootstrapClass) == classSuper(t, class_));

  expect(t, classFixedSize(t, bootstrapClass) >= classFixedSize(t, class_));

  expect(t, (classVmFlags(t, class_) & HasFinalizerFlag) == 0);

  PROTECT(t, bootstrapClass);
  PROTECT(t, class_);

  ENTER(t, Thread::ExclusiveState);

  classVmFlags(t, bootstrapClass) &= ~BootstrapFlag;
  classVmFlags(t, bootstrapClass) |= classVmFlags(t, class_);
  classFlags(t, bootstrapClass) |= classFlags(t, class_);

  set(t, bootstrapClass, ClassSuper, classSuper(t, class_));
  set(t, bootstrapClass, ClassInterfaceTable, classInterfaceTable(t, class_));
  set(t, bootstrapClass, ClassVirtualTable, classVirtualTable(t, class_));
  set(t, bootstrapClass, ClassFieldTable, classFieldTable(t, class_));
  set(t, bootstrapClass, ClassMethodTable, classMethodTable(t, class_));
  set(t, bootstrapClass, ClassStaticTable, classStaticTable(t, class_));
  set(t, bootstrapClass, ClassAddendum, classAddendum(t, class_));

  updateClassTables(t, bootstrapClass, class_);
}

object
makeArrayClass(Thread* t, object loader, unsigned dimensions, object spec,
               object elementClass)
{
  if (classVmFlags(t, type(t, Machine::JobjectType)) & BootstrapFlag) {
    PROTECT(t, loader);
    PROTECT(t, spec);
    PROTECT(t, elementClass);

    // Load java.lang.Object if present so we can use its vtable, but
    // don't throw an exception if we can't find it.  This way, we
    // avoid infinite recursion due to trying to create an array to
    // make a stack trace for a ClassNotFoundException.
    resolveSystemClass
      (t, root(t, Machine::BootLoader),
       className(t, type(t, Machine::JobjectType)), false);
  }

  object vtable = classVirtualTable(t, type(t, Machine::JobjectType));

  object c = t->m->processor->makeClass
    (t,
     0,
     0,
     2 * BytesPerWord,
     BytesPerWord,
     dimensions,
     classObjectMask(t, type(t, Machine::ArrayType)),
     spec,
     0,
     type(t, Machine::JobjectType),
     root(t, Machine::ArrayInterfaceTable),
     vtable,
     0,
     0,
     0,
     elementClass,
     loader,
     arrayLength(t, vtable));

  PROTECT(t, c);

  t->m->processor->initVtable(t, c);

  return c;
}

void
saveLoadedClass(Thread* t, object loader, object c)
{
  PROTECT(t, loader);
  PROTECT(t, c);

  ACQUIRE(t, t->m->classLock);

  if (classLoaderMap(t, loader) == 0) {
    object map = makeHashMap(t, 0, 0);
    set(t, loader, ClassLoaderMap, map);
  }

  hashMapInsert
    (t, classLoaderMap(t, loader), className(t, c), c, byteArrayHash);
}

object
makeArrayClass(Thread* t, object loader, object spec, bool throw_,
               Machine::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  const char* s = reinterpret_cast<const char*>(&byteArrayBody(t, spec, 0));
  const char* start = s;
  unsigned dimensions = 0;
  for (; *s == '['; ++s) ++ dimensions;

  object elementSpec;
  switch (*s) {
  case 'L': {
    ++ s;
    const char* elementSpecStart = s;
    while (*s and *s != ';') ++ s;
    
    elementSpec = makeByteArray(t, s - elementSpecStart + 1);
    memcpy(&byteArrayBody(t, elementSpec, 0),
           &byteArrayBody(t, spec, elementSpecStart - start),
           s - elementSpecStart);
    byteArrayBody(t, elementSpec, s - elementSpecStart) = 0;
  } break;

  default:
    if (dimensions > 1) {
      char c = *s;
      elementSpec = makeByteArray(t, dimensions + 1);
      unsigned i;
      for (i = 0; i < dimensions - 1; ++i) {
        byteArrayBody(t, elementSpec, i) = '[';
      }
      byteArrayBody(t, elementSpec, i++) = c;
      byteArrayBody(t, elementSpec, i) = 0;
      -- dimensions;
    } else {
      abort(t);
    }
  }

  object elementClass = hashMapFind
    (t, root(t, Machine::BootstrapClassMap), elementSpec, byteArrayHash,
     byteArrayEqual);
  
  if (elementClass == 0) {
    elementClass = resolveClass(t, loader, elementSpec, throw_, throwType);
    if (elementClass == 0) return 0;
  }

  PROTECT(t, elementClass);

  ACQUIRE(t, t->m->classLock);

  object class_ = findLoadedClass(t, classLoader(t, elementClass), spec);
  if (class_) {
    return class_;
  }

  class_ = makeArrayClass
    (t, classLoader(t, elementClass), dimensions, spec, elementClass);

  PROTECT(t, class_);

  saveLoadedClass(t, classLoader(t, elementClass), class_);

  return class_;
}

object
resolveArrayClass(Thread* t, object loader, object spec, bool throw_,
                  Machine::Type throwType)
{
  object c = hashMapFind
    (t, root(t, Machine::BootstrapClassMap), spec, byteArrayHash,
     byteArrayEqual);

  if (c) {
    set(t, c, ClassVirtualTable,
        classVirtualTable(t, type(t, Machine::JobjectType)));

    return c;
  } else {
    PROTECT(t, loader);
    PROTECT(t, spec);

    c = findLoadedClass(t, root(t, Machine::BootLoader), spec);

    if (c) {
      return c;
    } else {
      return makeArrayClass(t, loader, spec, throw_, throwType);
    }
  }
}

void
removeMonitor(Thread* t, object o)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  object m = hashMapRemove
    (t, root(t, Machine::MonitorMap), o, objectHash, objectEqual);

  if (DebugMonitors) {
    fprintf(stderr, "dispose monitor %p for object %x\n", m, hash);
  }
}

void
removeString(Thread* t, object o)
{
  hashMapRemove(t, root(t, Machine::StringMap), o, stringHash, objectEqual);
}

void
bootClass(Thread* t, Machine::Type type, int superType, uint32_t objectMask,
          unsigned fixedSize, unsigned arrayElementSize, unsigned vtableLength)
{
  object super = (superType >= 0
                  ? vm::type(t, static_cast<Machine::Type>(superType)) : 0);

  object mask;
  if (objectMask) {
    if (super
        and classObjectMask(t, super)
        and intArrayBody(t, classObjectMask(t, super), 0)
        == static_cast<int32_t>(objectMask))
    {
      mask = classObjectMask
        (t, vm::type(t, static_cast<Machine::Type>(superType)));
    } else {
      mask = makeIntArray(t, 1);
      intArrayBody(t, mask, 0) = objectMask;
    }
  } else {
    mask = 0;
  }

  super = (superType >= 0
           ? vm::type(t, static_cast<Machine::Type>(superType)) : 0);

  object class_ = t->m->processor->makeClass
    (t, 0, BootstrapFlag, fixedSize, arrayElementSize,
     arrayElementSize ? 1 : 0, mask, 0, 0, super, 0, 0, 0, 0, 0, 0,
     root(t, Machine::BootLoader), vtableLength);

  setType(t, type, class_);
}

void
bootJavaClass(Thread* t, Machine::Type type, int superType, const char* name,
              int vtableLength, object bootMethod)
{
  PROTECT(t, bootMethod);

  object n = makeByteArray(t, name);
  PROTECT(t, n);

  object class_ = vm::type(t, type);
  PROTECT(t, class_);

  set(t, class_, ClassName, n);

  object vtable;
  if (vtableLength >= 0) {
    vtable = makeArray(t, vtableLength);
    for (int i = 0; i < vtableLength; ++ i) {
      arrayBody(t, vtable, i) = bootMethod;
    }
  } else {
    vtable = classVirtualTable
      (t, vm::type(t, static_cast<Machine::Type>(superType)));
  }

  set(t, class_, ClassVirtualTable, vtable);

  t->m->processor->initVtable(t, class_);

  hashMapInsert
    (t, root(t, Machine::BootstrapClassMap), n, class_, byteArrayHash);
}

void
nameClass(Thread* t, Machine::Type type, const char* name)
{
  object n = makeByteArray(t, name);
  set(t, arrayBody(t, t->m->types, type), ClassName, n);
}

void
makeArrayInterfaceTable(Thread* t)
{
  object interfaceTable = makeArray(t, 4);
  
  set(t, interfaceTable, ArrayBody, type
      (t, Machine::SerializableType));
  
  set(t, interfaceTable, ArrayBody + (2 * BytesPerWord),
      type(t, Machine::CloneableType));
  
  setRoot(t, Machine::ArrayInterfaceTable, interfaceTable);
}

void
boot(Thread* t)
{
  Machine* m = t->m;

  m->unsafe = true;

  m->roots = allocate(t, pad((Machine::RootCount + 2) * BytesPerWord), true);
  arrayLength(t, m->roots) = Machine::RootCount;

  setRoot(t, Machine::BootLoader,
          allocate(t, FixedSizeOfSystemClassLoader, true));

  setRoot(t, Machine::AppLoader,
          allocate(t, FixedSizeOfSystemClassLoader, true));

  m->types = allocate(t, pad((TypeCount + 2) * BytesPerWord), true);
  arrayLength(t, m->types) = TypeCount;

#include "type-initializations.cpp"

  object arrayClass = type(t, Machine::ArrayType);
  set(t, m->types, 0, arrayClass);
  set(t, m->roots, 0, arrayClass);

  object loaderClass = type(t, Machine::SystemClassLoaderType);
  set(t, root(t, Machine::BootLoader), 0, loaderClass);
  set(t, root(t, Machine::AppLoader), 0, loaderClass);

  object objectClass = type(t, Machine::JobjectType);

  object classClass = type(t, Machine::ClassType);
  set(t, classClass, 0, classClass);
  set(t, classClass, ClassSuper, objectClass);

  object intArrayClass = type(t, Machine::IntArrayType);
  set(t, intArrayClass, 0, classClass);
  set(t, intArrayClass, ClassSuper, objectClass);

  m->unsafe = false;

  classVmFlags(t, type(t, Machine::SingletonType))
    |= SingletonFlag;

  classVmFlags(t, type(t, Machine::ContinuationType))
    |= ContinuationFlag;

  classVmFlags(t, type(t, Machine::JreferenceType))
    |= ReferenceFlag;
  classVmFlags(t, type(t, Machine::WeakReferenceType))
    |= ReferenceFlag | WeakReferenceFlag;
  classVmFlags(t, type(t, Machine::SoftReferenceType))
    |= ReferenceFlag | WeakReferenceFlag;
  classVmFlags(t, type(t, Machine::PhantomReferenceType))
    |= ReferenceFlag | WeakReferenceFlag;

  classVmFlags(t, type(t, Machine::JbooleanType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JbyteType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JcharType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JshortType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JintType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JlongType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JfloatType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JdoubleType))
    |= PrimitiveFlag;
  classVmFlags(t, type(t, Machine::JvoidType))
    |= PrimitiveFlag;

  set(t, type(t, Machine::BooleanArrayType), ClassStaticTable,
      type(t, Machine::JbooleanType));
  set(t, type(t, Machine::ByteArrayType), ClassStaticTable,
      type(t, Machine::JbyteType));
  set(t, type(t, Machine::CharArrayType), ClassStaticTable,
      type(t, Machine::JcharType));
  set(t, type(t, Machine::ShortArrayType), ClassStaticTable,
      type(t, Machine::JshortType));
  set(t, type(t, Machine::IntArrayType), ClassStaticTable,
      type(t, Machine::JintType));
  set(t, type(t, Machine::LongArrayType), ClassStaticTable,
      type(t, Machine::JlongType));
  set(t, type(t, Machine::FloatArrayType), ClassStaticTable,
      type(t, Machine::JfloatType));
  set(t, type(t, Machine::DoubleArrayType), ClassStaticTable,
      type(t, Machine::JdoubleType));

  { object map = makeHashMap(t, 0, 0);
    set(t, root(t, Machine::BootLoader), ClassLoaderMap, map);
  }

  systemClassLoaderFinder(t, root(t, Machine::BootLoader)) = m->bootFinder;

  { object map = makeHashMap(t, 0, 0);
    set(t, root(t, Machine::AppLoader), ClassLoaderMap, map);
  }

  systemClassLoaderFinder(t, root(t, Machine::AppLoader)) = m->appFinder;

  set(t, root(t, Machine::AppLoader), ClassLoaderParent,
      root(t, Machine::BootLoader));

  setRoot(t, Machine::BootstrapClassMap, makeHashMap(t, 0, 0));

  setRoot(t, Machine::StringMap, makeWeakHashMap(t, 0, 0));

  makeArrayInterfaceTable(t);

  set(t, type(t, Machine::BooleanArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::ByteArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::CharArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::ShortArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::IntArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::LongArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::FloatArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, type(t, Machine::DoubleArrayType), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));

  m->processor->boot(t, 0, 0);

  { object bootCode = makeCode(t, 0, 0, 0, 0, 0, 0, 0, 1);
    codeBody(t, bootCode, 0) = impdep1;
    object bootMethod = makeMethod
      (t, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, bootCode);
    PROTECT(t, bootMethod);

#include "type-java-initializations.cpp"

    //#ifdef AVIAN_HEAPDUMP
#  include "type-name-initializations.cpp"
      //#endif
  }
}

class HeapClient: public Heap::Client {
 public:
  HeapClient(Machine* m): m(m) { }

  virtual void visitRoots(Heap::Visitor* v) {
    ::visitRoots(m, v);

    postVisit(m->rootThread, v);
  }

  virtual void collect(void* context, Heap::CollectionType type) {
    collect(static_cast<Thread*>(context), type);
  }

  virtual bool isFixed(void* p) {
    return objectFixed(m->rootThread, static_cast<object>(p));
  }

  virtual unsigned sizeInWords(void* p) {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));

    unsigned n = baseSize(t, o, static_cast<object>
                          (m->heap->follow(objectClass(t, o))));

    if (objectExtended(t, o)) {
      ++ n;
    }

    return n;
  }

  virtual unsigned copiedSizeInWords(void* p) {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));
    assert(t, not objectFixed(t, o));

    unsigned n = baseSize(t, o, static_cast<object>
                          (m->heap->follow(objectClass(t, o))));

    if (objectExtended(t, o) or hashTaken(t, o)) {
      ++ n;
    }

    return n;
  }

  virtual void copy(void* srcp, void* dstp) {
    Thread* t = m->rootThread;

    object src = static_cast<object>(m->heap->follow(maskAlignedPointer(srcp)));
    assert(t, not objectFixed(t, src));

    object class_ = static_cast<object>
      (m->heap->follow(objectClass(t, src)));

    unsigned base = baseSize(t, src, class_);
    unsigned n = extendedSize(t, src, base);

    object dst = static_cast<object>(dstp);

    memcpy(dst, src, n * BytesPerWord);

    if (hashTaken(t, src)) {
      alias(dst, 0) &= PointerMask;
      alias(dst, 0) |= ExtendedMark;
      extendedWord(t, dst, base) = takeHash(t, src);
    }
  }

  virtual void walk(void* p, Heap::Walker* w) {
    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));
    ::walk(m->rootThread, w, o, 0);
  }

  void dispose() {
    m->heap->free(this, sizeof(*this));
  }
    
 private:
  Machine* m;
};

void
doCollect(Thread* t, Heap::CollectionType type, int pendingAllocation)
{
  expect(t, not t->m->collecting);

  t->m->collecting = true;
  THREAD_RESOURCE0(t, t->m->collecting = false);

#ifdef VM_STRESS
  bool stress = (t->flags & Thread::StressFlag) != 0;
  if (not stress) atomicOr(&(t->flags), Thread::StressFlag);
#endif

  Machine* m = t->m;

  m->unsafe = true;
  m->heap->collect(type, footprint(m->rootThread), pendingAllocation
                   - (t->m->heapPoolIndex * ThreadHeapSizeInWords));
  m->unsafe = false;

  postCollect(m->rootThread);

  killZombies(t, m->rootThread);

  for (unsigned i = 0; i < m->heapPoolIndex; ++i) {
    m->heap->free(m->heapPool[i], ThreadHeapSizeInBytes);
  }
  m->heapPoolIndex = 0;

  if (m->heap->limitExceeded()) {
    // if we're out of memory, disallow further allocations of fixed
    // objects:
    m->fixedFootprint = FixedFootprintThresholdInBytes;
  } else {
    m->fixedFootprint = 0;
  }

#ifdef VM_STRESS
  if (not stress) atomicAnd(&(t->flags), ~Thread::StressFlag);
#endif

  object finalizeQueue = t->m->finalizeQueue;
  t->m->finalizeQueue = 0;
  for (; finalizeQueue; finalizeQueue = finalizerNext(t, finalizeQueue)) {
    void (*function)(Thread*, object);
    memcpy(&function, &finalizerFinalize(t, finalizeQueue), BytesPerWord);
    function(t, finalizerTarget(t, finalizeQueue));
  }

  if ((root(t, Machine::ObjectsToFinalize) or root(t, Machine::ObjectsToClean))
      and m->finalizeThread == 0
      and t->state != Thread::ExitState)
  {
    m->finalizeThread = m->processor->makeThread
      (m, root(t, Machine::FinalizerThread), m->rootThread);
    
    addThread(t, m->finalizeThread);

    if (not startThread(t, m->finalizeThread)) {
      removeThread(t, m->finalizeThread);
      m->finalizeThread = 0;
    }
  }
}

uint64_t
invokeLoadClass(Thread* t, uintptr_t* arguments)
{
  object method = reinterpret_cast<object>(arguments[0]);
  object loader = reinterpret_cast<object>(arguments[1]);
  object specString = reinterpret_cast<object>(arguments[2]);

  return reinterpret_cast<uintptr_t>
    (t->m->processor->invoke(t, method, loader, specString));
}

bool
isInitializing(Thread* t, object c)
{
  for (Thread::ClassInitStack* s = t->classInitStack; s; s = s->next) {
    if (s->class_ == c) {
      return true;
    }
  }
  return false;
}

object
findInTable(Thread* t, object table, object name, object spec,
            object& (*getName)(Thread*, object),
            object& (*getSpec)(Thread*, object))
{
  if (table) {
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object o = arrayBody(t, table, i);      
      if (vm::strcmp(&byteArrayBody(t, getName(t, o), 0),
                     &byteArrayBody(t, name, 0)) == 0 and
          vm::strcmp(&byteArrayBody(t, getSpec(t, o), 0),
                     &byteArrayBody(t, spec, 0)) == 0)
      {
        return o;
      }
    }

//     fprintf(stderr, "%s %s not in\n",
//             &byteArrayBody(t, name, 0),
//             &byteArrayBody(t, spec, 0));

//     for (unsigned i = 0; i < arrayLength(t, table); ++i) {
//       object o = arrayBody(t, table, i); 
//       fprintf(stderr, "\t%s %s\n",
//               &byteArrayBody(t, getName(t, o), 0),
//               &byteArrayBody(t, getSpec(t, o), 0)); 
//     }
  }

  return 0;
}

} // namespace

namespace vm {

Machine::Machine(System* system, Heap* heap, Finder* bootFinder,
                 Finder* appFinder, Processor* processor, Classpath* classpath,
                 const char** properties, unsigned propertyCount,
                 const char** arguments, unsigned argumentCount,
                 unsigned stackSizeInBytes):
  vtable(&javaVMVTable),
  system(system),
  heapClient(new (heap->allocate(sizeof(HeapClient)))
             HeapClient(this)),
  heap(heap),
  bootFinder(bootFinder),
  appFinder(appFinder),
  processor(processor),
  classpath(classpath),
  rootThread(0),
  exclusive(0),
  finalizeThread(0),
  jniReferences(0),
  properties(properties),
  propertyCount(propertyCount),
  arguments(arguments),
  argumentCount(argumentCount),
  threadCount(0),
  activeCount(0),
  liveCount(0),
  daemonCount(0),
  fixedFootprint(0),
  stackSizeInBytes(stackSizeInBytes),
  localThread(0),
  stateLock(0),
  heapLock(0),
  classLock(0),
  referenceLock(0),
  shutdownLock(0),
  libraries(0),
  errorLog(0),
  bootimage(0),
  types(0),
  roots(0),
  finalizers(0),
  tenuredFinalizers(0),
  finalizeQueue(0),
  weakReferences(0),
  tenuredWeakReferences(0),
  unsafe(false),
  collecting(false),
  triedBuiltinOnLoad(false),
  dumpedHeapOnOOM(false),
  alive(true),
  heapPoolIndex(0)
{
  heap->setClient(heapClient);

  populateJNITables(&javaVMVTable, &jniEnvVTable);

  const char* bootstrapProperty = findProperty(this, BOOTSTRAP_PROPERTY);
  const char* bootstrapPropertyDup = bootstrapProperty ? strdup(bootstrapProperty) : 0;
  const char* bootstrapPropertyEnd = bootstrapPropertyDup + (bootstrapPropertyDup ? strlen(bootstrapPropertyDup) : 0);
  char* codeLibraryName = (char*)bootstrapPropertyDup;
  char* codeLibraryNameEnd = 0;
  if (codeLibraryName && (codeLibraryNameEnd = strchr(codeLibraryName, system->pathSeparator())))
    *codeLibraryNameEnd = 0;

  if (not system->success(system->make(&localThread)) or
      not system->success(system->make(&stateLock)) or
      not system->success(system->make(&heapLock)) or
      not system->success(system->make(&classLock)) or
      not system->success(system->make(&referenceLock)) or
      not system->success(system->make(&shutdownLock)) or
      not system->success
      (system->load(&libraries, bootstrapPropertyDup)))
  {
    system->abort();
  }

  System::Library* additionalLibrary = 0;
  while (codeLibraryNameEnd && codeLibraryNameEnd + 1 < bootstrapPropertyEnd) {
    codeLibraryName = codeLibraryNameEnd + 1;
    codeLibraryNameEnd = strchr(codeLibraryName, system->pathSeparator());
    if (codeLibraryNameEnd)
      *codeLibraryNameEnd = 0;

    if (!system->success(system->load(&additionalLibrary, codeLibraryName)))
      system->abort();
    libraries->setNext(additionalLibrary);
  }

  if(bootstrapPropertyDup)
    free((void*)bootstrapPropertyDup);
}

void
Machine::dispose()
{
  localThread->dispose();
  stateLock->dispose();
  heapLock->dispose();
  classLock->dispose();
  referenceLock->dispose();
  shutdownLock->dispose();

  if (libraries) {
    libraries->disposeAll();
  }

  for (Reference* r = jniReferences; r;) {
    Reference* tmp = r;
    r = r->next;
    heap->free(tmp, sizeof(*tmp));
  }

  for (unsigned i = 0; i < heapPoolIndex; ++i) {
    heap->free(heapPool[i], ThreadHeapSizeInBytes);
  }

  if (bootimage) {
    heap->free(bootimage, bootimageSize);
  }

  heap->free(arguments, sizeof(const char*) * argumentCount);

  heap->free(properties, sizeof(const char*) * propertyCount);

  static_cast<HeapClient*>(heapClient)->dispose();

  heap->free(this, sizeof(*this));
}

Thread::Thread(Machine* m, object javaThread, Thread* parent):
  vtable(&(m->jniEnvVTable)),
  m(m),
  parent(parent),
  peer(0),
  child(0),
  waitNext(0),
  state(NoState),
  criticalLevel(0),
  systemThread(0),
  lock(0),
  javaThread(javaThread),
  exception(0),
  heapIndex(0),
  heapOffset(0),
  protector(0),
  classInitStack(0),
  runnable(this),
  defaultHeap(static_cast<uintptr_t*>
              (m->heap->allocate(ThreadHeapSizeInBytes))),
  heap(defaultHeap),
  backupHeapIndex(0),
  flags(ActiveFlag)
{ }

void
Thread::init()
{
  memset(defaultHeap, 0, ThreadHeapSizeInBytes);
  memset(backupHeap, 0, ThreadBackupHeapSizeInBytes);

  if (parent == 0) {
    assert(this, m->rootThread == 0);
    assert(this, javaThread == 0);

    m->rootThread = this;
    m->unsafe = true;

    if (not m->system->success(m->system->attach(&runnable))) {
      abort(this);
    }

    BootImage* image = 0;
    uint8_t* code = 0;
    const char* imageFunctionName = findProperty(m, "avian.bootimage");
    if (imageFunctionName) {
      bool lzma = strncmp("lzma:", imageFunctionName, 5) == 0;
      const char* symbolName
        = lzma ? imageFunctionName + 5 : imageFunctionName;

      void* imagep = m->libraries->resolve(symbolName);
      if (imagep) {
        uint8_t* (*imageFunction)(unsigned*);
        memcpy(&imageFunction, &imagep, BytesPerWord);

        unsigned size;
        uint8_t* imageBytes = imageFunction(&size);
        if (lzma) {
#ifdef AVIAN_USE_LZMA
          m->bootimage = image = reinterpret_cast<BootImage*>
            (decodeLZMA
             (m->system, m->heap, imageBytes, size, &(m->bootimageSize)));
#else
          abort(this);
#endif
        } else {
          image = reinterpret_cast<BootImage*>(imageBytes);
        }

        const char* codeFunctionName = findProperty(m, "avian.codeimage");
        if (codeFunctionName) {
          void* codep = m->libraries->resolve(codeFunctionName);
          if (codep) {
            uint8_t* (*codeFunction)(unsigned*);
            memcpy(&codeFunction, &codep, BytesPerWord);

            code = codeFunction(&size);
          }
        }
      }
    }

    m->unsafe = false;

    enter(this, ActiveState);

    if (image and code) {
      m->processor->boot(this, image, code);
      makeArrayInterfaceTable(this);
    } else {
      boot(this);
    }

    setRoot(this, Machine::ByteArrayMap, makeWeakHashMap(this, 0, 0));
    setRoot(this, Machine::MonitorMap, makeWeakHashMap(this, 0, 0));

    setRoot(this, Machine::ClassRuntimeDataTable, makeVector(this, 0, 0));
    setRoot(this, Machine::MethodRuntimeDataTable, makeVector(this, 0, 0));
    setRoot(this, Machine::JNIMethodTable, makeVector(this, 0, 0));
    setRoot(this, Machine::JNIFieldTable, makeVector(this, 0, 0));

    m->localThread->set(this);
  }

  expect(this, m->system->success(m->system->make(&lock)));
}

void
Thread::exit()
{
  if (state != Thread::ExitState and
      state != Thread::ZombieState)
  {
    enter(this, Thread::ExclusiveState);

    if (m->liveCount == 1) {
      turnOffTheLights(this);
    } else {
      threadPeer(this, javaThread) = 0;

      enter(this, Thread::ZombieState);
    }
  }
}

void
Thread::dispose()
{
  if (lock) {
    lock->dispose();
  }
  
  if (systemThread) {
    systemThread->dispose();
  }

  -- m->threadCount;

  m->heap->free(defaultHeap, ThreadHeapSizeInBytes);

  m->processor->dispose(this);
}

void
shutDown(Thread* t)
{
  ACQUIRE(t, t->m->shutdownLock);

  object hooks = root(t, Machine::ShutdownHooks);
  PROTECT(t, hooks);

  setRoot(t, Machine::ShutdownHooks, 0);

  object h = hooks;
  PROTECT(t, h);
  for (; h; h = pairSecond(t, h)) {
    startThread(t, pairFirst(t, h));
  }

  // wait for hooks to exit
  h = hooks;
  for (; h; h = pairSecond(t, h)) {
    while (true) {
      Thread* ht = reinterpret_cast<Thread*>(threadPeer(t, pairFirst(t, h)));

      { ACQUIRE(t, t->m->stateLock);

        if (ht == 0
            or ht->state == Thread::ZombieState
            or ht->state == Thread::JoinedState)
        {
          break;
        } else {
          ENTER(t, Thread::IdleState);
          t->m->stateLock->wait(t->systemThread, 0);
        }
      }
    }
  }

  // tell finalize thread to exit and wait for it to do so
  { ACQUIRE(t, t->m->stateLock);
    Thread* finalizeThread = t->m->finalizeThread;
    if (finalizeThread) {
      t->m->finalizeThread = 0;
      t->m->stateLock->notifyAll(t->systemThread);

      while (finalizeThread->state != Thread::ZombieState
             and finalizeThread->state != Thread::JoinedState)
      {
        ENTER(t, Thread::IdleState);
        t->m->stateLock->wait(t->systemThread, 0);      
      }
    }
  }

  // interrupt daemon threads and tell them to die

  // todo: be more aggressive about killing daemon threads, e.g. at
  // any GC point, not just at waits/sleeps
  { ACQUIRE(t, t->m->stateLock);

    t->m->alive = false;

    visitAll(t, t->m->rootThread, interruptDaemon);
  }
}

void
enter(Thread* t, Thread::State s)
{
  stress(t);

  if (s == t->state) return;

  if (t->state == Thread::ExitState) {
    // once in exit state, we stay that way
    return;
  }

#ifdef USE_ATOMIC_OPERATIONS
#  define INCREMENT atomicIncrement
#  define ACQUIRE_LOCK ACQUIRE_RAW(t, t->m->stateLock)
#  define STORE_LOAD_MEMORY_BARRIER storeLoadMemoryBarrier()
#else
#  define INCREMENT(pointer, value) *(pointer) += value;
#  define ACQUIRE_LOCK
#  define STORE_LOAD_MEMORY_BARRIER

  ACQUIRE_RAW(t, t->m->stateLock);
#endif // not USE_ATOMIC_OPERATIONS

  switch (s) {
  case Thread::ExclusiveState: {
    ACQUIRE_LOCK;

    while (t->m->exclusive) {
      // another thread got here first.
      ENTER(t, Thread::IdleState);
      t->m->stateLock->wait(t->systemThread, 0);
    }

    switch (t->state) {
    case Thread::ActiveState: break;

    case Thread::IdleState: {
      INCREMENT(&(t->m->activeCount), 1);
    } break;

    default: abort(t);
    }

    t->state = Thread::ExclusiveState;
    t->m->exclusive = t;
    
    STORE_LOAD_MEMORY_BARRIER;

    while (t->m->activeCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  } break;

  case Thread::IdleState:
    if (LIKELY(t->state == Thread::ActiveState)) {
      // fast path
      assert(t, t->m->activeCount > 0);
      INCREMENT(&(t->m->activeCount), -1);

      t->state = s;

      if (t->m->exclusive) {
        ACQUIRE_LOCK;

        t->m->stateLock->notifyAll(t->systemThread);
      }

      break;
    } else {
      // fall through to slow path
    }

  case Thread::ZombieState: {
    ACQUIRE_LOCK;

    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->m->exclusive == t);
      t->m->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assert(t, t->m->activeCount > 0);
    INCREMENT(&(t->m->activeCount), -1);

    if (s == Thread::ZombieState) {
      assert(t, t->m->liveCount > 0);
      -- t->m->liveCount;

      if (t->flags & Thread::DaemonFlag) {
        -- t->m->daemonCount;
      }
    }

    t->state = s;

    t->m->stateLock->notifyAll(t->systemThread);
  } break;

  case Thread::ActiveState:
    if (LIKELY(t->state == Thread::IdleState and t->m->exclusive == 0)) {
      // fast path
      INCREMENT(&(t->m->activeCount), 1);

      t->state = s;

      if (t->m->exclusive) {
        // another thread has entered the exclusive state, so we
        // return to idle and use the slow path to become active
        enter(t, Thread::IdleState);
      } else {
        break;
      }
    }

    { ACQUIRE_LOCK;

      switch (t->state) {
      case Thread::ExclusiveState: {
        assert(t, t->m->exclusive == t);

        t->state = s;
        t->m->exclusive = 0;

        t->m->stateLock->notifyAll(t->systemThread);
      } break;

      case Thread::NoState:
      case Thread::IdleState: {
        while (t->m->exclusive) {
          t->m->stateLock->wait(t->systemThread, 0);
        }

        INCREMENT(&(t->m->activeCount), 1);
        if (t->state == Thread::NoState) {
          ++ t->m->liveCount;
          ++ t->m->threadCount;
        }
        t->state = s;
      } break;

      default: abort(t);
      }
    } break;

  case Thread::ExitState: {
    ACQUIRE_LOCK;

    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->m->exclusive == t);
      // exit state should also be exclusive, so don't set exclusive = 0

      t->m->stateLock->notifyAll(t->systemThread);
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assert(t, t->m->activeCount > 0);
    INCREMENT(&(t->m->activeCount), -1);

    t->state = s;

    while (t->m->liveCount - t->m->daemonCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  } break;

  default: abort(t);
  }
}

object
allocate2(Thread* t, unsigned sizeInBytes, bool objectMask)
{
  return allocate3
    (t, t->m->heap,
     ceilingDivide(sizeInBytes, BytesPerWord) > ThreadHeapSizeInWords ?
     Machine::FixedAllocation : Machine::MovableAllocation,
     sizeInBytes, objectMask);
}

object
allocate3(Thread* t, Allocator* allocator, Machine::AllocationType type,
          unsigned sizeInBytes, bool objectMask)
{
  expect(t, t->criticalLevel == 0);

  if (UNLIKELY(t->flags & Thread::UseBackupHeapFlag)) {
    expect(t,  t->backupHeapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
           <= ThreadBackupHeapSizeInWords);
    
    object o = reinterpret_cast<object>(t->backupHeap + t->backupHeapIndex);
    t->backupHeapIndex += ceilingDivide(sizeInBytes, BytesPerWord);
    fieldAtOffset<object>(o, 0) = 0;
    return o;
  } else if (UNLIKELY(t->flags & Thread::TracingFlag)) {
    expect(t, t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
           <= ThreadHeapSizeInWords);
    return allocateSmall(t, sizeInBytes);
  }

  ACQUIRE_RAW(t, t->m->stateLock);

  while (t->m->exclusive and t->m->exclusive != t) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    ENTER(t, Thread::IdleState);

    while (t->m->exclusive) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  }
  
  do {
    switch (type) {
    case Machine::MovableAllocation:
      if (t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
          > ThreadHeapSizeInWords)
      {
        t->heap = 0;
        if ((not t->m->heap->limitExceeded())
            and t->m->heapPoolIndex < ThreadHeapPoolSize)
        {
          t->heap = static_cast<uintptr_t*>
            (t->m->heap->tryAllocate(ThreadHeapSizeInBytes));

          if (t->heap) {
            memset(t->heap, 0, ThreadHeapSizeInBytes);

            t->m->heapPool[t->m->heapPoolIndex++] = t->heap;
            t->heapOffset += t->heapIndex;
            t->heapIndex = 0;
          }
        }
      }
      break;

    case Machine::FixedAllocation:
      if (t->m->fixedFootprint + sizeInBytes > FixedFootprintThresholdInBytes)
      {
        t->heap = 0;
      }
      break;

    case Machine::ImmortalAllocation:
      break;
    }

    int pendingAllocation = t->m->heap->fixedFootprint
      (ceilingDivide(sizeInBytes, BytesPerWord), objectMask);

    if (t->heap == 0 or t->m->heap->limitExceeded(pendingAllocation)) {
      //     fprintf(stderr, "gc");
      //     vmPrintTrace(t);
      collect(t, Heap::MinorCollection, pendingAllocation);
    }

    if (t->m->heap->limitExceeded(pendingAllocation)) {
      throw_(t, root(t, Machine::OutOfMemoryError));
    }
  } while (type == Machine::MovableAllocation
           and t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
           > ThreadHeapSizeInWords);

  switch (type) {
  case Machine::MovableAllocation: {
    return allocateSmall(t, sizeInBytes);
  }

  case Machine::FixedAllocation: {
    object o = static_cast<object>
      (t->m->heap->allocateFixed
       (allocator, ceilingDivide(sizeInBytes, BytesPerWord), objectMask));

    memset(o, 0, sizeInBytes);

    alias(o, 0) = FixedMark;
    
    t->m->fixedFootprint += t->m->heap->fixedFootprint
      (ceilingDivide(sizeInBytes, BytesPerWord), objectMask);
      
    return o;
  }

  case Machine::ImmortalAllocation: {
    object o = static_cast<object>
      (t->m->heap->allocateImmortalFixed
       (allocator, ceilingDivide(sizeInBytes, BytesPerWord), objectMask));

    memset(o, 0, sizeInBytes);

    alias(o, 0) = FixedMark;

    return o;
  }

  default: abort(t);
  }
}

void
collect(Thread* t, Heap::CollectionType type, int pendingAllocation)
{
  ENTER(t, Thread::ExclusiveState);

  unsigned pending = pendingAllocation
    - (t->m->heapPoolIndex * ThreadHeapSizeInWords);

  if (t->m->heap->limitExceeded(pending)) {
    type = Heap::MajorCollection;
  }

  doCollect(t, type, pendingAllocation);

  if (t->m->heap->limitExceeded(pending)) {
    // try once more, giving the heap a chance to squeeze everything
    // into the smallest possible space:
    doCollect(t, Heap::MajorCollection, pendingAllocation);
  }
}

object
makeNewGeneral(Thread* t, object class_)
{
  assert(t, t->state == Thread::ActiveState);

  PROTECT(t, class_);

  object instance = makeNew(t, class_);
  PROTECT(t, instance);

  if (classVmFlags(t, class_) & WeakReferenceFlag) {
    ACQUIRE(t, t->m->referenceLock);
    
    jreferenceVmNext(t, instance) = t->m->weakReferences;
    t->m->weakReferences = instance;
  }

  if (classVmFlags(t, class_) & HasFinalizerFlag) {
    addFinalizer(t, instance, 0);
  }

  return instance;
}

void
popResources(Thread* t)
{
  while (t->resource != t->checkpoint->resource) {
    Thread::Resource* r = t->resource;
    t->resource = r->next;
    r->release();
  }

  t->protector = t->checkpoint->protector;
}

object
makeByteArrayV(Thread* t, const char* format, va_list a, int size)
{
  THREAD_RUNTIME_ARRAY(t, char, buffer, size);
  
  int r = vm::vsnprintf(RUNTIME_ARRAY_BODY(buffer), size - 1, format, a);
  if (r >= 0 and r < size - 1) {
    object s = makeByteArray(t, strlen(RUNTIME_ARRAY_BODY(buffer)) + 1);
    memcpy(&byteArrayBody(t, s, 0), RUNTIME_ARRAY_BODY(buffer),
           byteArrayLength(t, s));
    return s;
  } else {
    return 0;
  }
}

object
makeByteArray(Thread* t, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    object s = makeByteArrayV(t, format, a, size);
    va_end(a);

    if (s) {
      return s;
    } else {
      size *= 2;
    }
  }
}

object
makeString(Thread* t, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    object s = makeByteArrayV(t, format, a, size);
    va_end(a);

    if (s) {
      return t->m->classpath->makeString(t, s, 0, byteArrayLength(t, s) - 1);
    } else {
      size *= 2;
    }
  }
}

int
stringUTFLength(Thread* t, object string, unsigned start, unsigned length)
{
  unsigned result = 0;

  if (length) {
    object data = stringData(t, string);
    if (objectClass(t, data) == type(t, Machine::ByteArrayType)) {
      result = length;
    } else {
      for (unsigned i = 0; i < length; ++i) {
        uint16_t c = charArrayBody
          (t, data, stringOffset(t, string) + start + i);
        if (c == 0)         result += 1; // null char (was 2 bytes in Java)
        else if (c < 0x80)  result += 1; // ASCII char
        else if (c < 0x800) result += 2; // two-byte char
        else                result += 3; // three-byte char
      }
    }
  }

  return result;
}

void
stringChars(Thread* t, object string, unsigned start, unsigned length,
            char* chars)
{
  if (length) {
    object data = stringData(t, string);
    if (objectClass(t, data) == type(t, Machine::ByteArrayType)) {
      memcpy(chars,
             &byteArrayBody(t, data, stringOffset(t, string) + start),
             length);
    } else {
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = charArrayBody(t, data, stringOffset(t, string) + start + i);
      }
    }
  }
  chars[length] = 0;
}

void
stringChars(Thread* t, object string, unsigned start, unsigned length,
            uint16_t* chars)
{
  if (length) {
    object data = stringData(t, string);
    if (objectClass(t, data) == type(t, Machine::ByteArrayType)) {
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = byteArrayBody(t, data, stringOffset(t, string) + start + i);
      }
    } else {
      memcpy(chars,
             &charArrayBody(t, data, stringOffset(t, string) + start),
             length * sizeof(uint16_t));
    }
  }
  chars[length] = 0;
}

void
stringUTFChars(Thread* t, object string, unsigned start, unsigned length,
               char* chars, unsigned charsLength UNUSED)
{
  assert(t, static_cast<unsigned>
         (stringUTFLength(t, string, start, length)) == charsLength);

  object data = stringData(t, string);
  if (objectClass(t, data) == type(t, Machine::ByteArrayType)) {    
    memcpy(chars,
           &byteArrayBody(t, data, stringOffset(t, string) + start),
           length);
    chars[length] = 0; 
  } else {
    int j = 0;
    for (unsigned i = 0; i < length; ++i) {
      uint16_t c = charArrayBody
        (t, data, stringOffset(t, string) + start + i);
      if(!c) {                // null char
        chars[j++] = 0;
      } else if (c < 0x80) {  // ASCII char
        chars[j++] = static_cast<char>(c);
      } else if (c < 0x800) { // two-byte char
        chars[j++] = static_cast<char>(0x0c0 | (c >> 6));
        chars[j++] = static_cast<char>(0x080 | (c & 0x03f));
      } else {                // three-byte char
        chars[j++] = static_cast<char>(0x0e0 | ((c >> 12) & 0x0f));
        chars[j++] = static_cast<char>(0x080 | ((c >> 6) & 0x03f));
        chars[j++] = static_cast<char>(0x080 | (c & 0x03f));
      }
    }
    chars[j] = 0;
  }    
}

uint64_t
resolveBootstrap(Thread* t, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);

  resolveSystemClass(t, root(t, Machine::BootLoader), name);

  return 1;
}

bool
isAssignableFrom(Thread* t, object a, object b)
{
  assert(t, a);
  assert(t, b);

  if (a == b) return true;

  if (classFlags(t, a) & ACC_INTERFACE) {
    if (classVmFlags(t, b) & BootstrapFlag) {
      uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(className(t, b)) };

      if (run(t, resolveBootstrap, arguments) == 0) {
        t->exception = 0;
        return false;
      }
    }

    object itable = classInterfaceTable(t, b);
    if (itable) {
      unsigned stride = (classFlags(t, b) & ACC_INTERFACE) ? 1 : 2;
      for (unsigned i = 0; i < arrayLength(t, itable); i += stride) {
        if (arrayBody(t, itable, i) == a) {
          return true;
        }
      }
    }
  } else if (classArrayDimensions(t, a)) {
    if (classArrayDimensions(t, b)) {
      return isAssignableFrom
        (t, classStaticTable(t, a), classStaticTable(t, b));
    }
  } else if ((classVmFlags(t, a) & PrimitiveFlag)
             == (classVmFlags(t, b) & PrimitiveFlag))
  {
    for (; b; b = classSuper(t, b)) {
      if (b == a) {
        return true;
      }
    }
  }

  return false;
}

bool
instanceOf(Thread* t, object class_, object o)
{
  if (o == 0) {
    return false;
  } else {
    return isAssignableFrom(t, class_, objectClass(t, o));
  }
}

object
classInitializer(Thread* t, object class_)
{
  if (classMethodTable(t, class_)) {
    for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, class_)); ++i)
    {
      object o = arrayBody(t, classMethodTable(t, class_), i);

      if (methodVmFlags(t, o) & ClassInitFlag) {
        return o;
      }               
    }
  }
  return 0;
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

object
parseClass(Thread* t, object loader, const uint8_t* data, unsigned size,
           Machine::Type throwType)
{
  PROTECT(t, loader);

  class Client: public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void NO_RETURN handleError() {
      abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, data, size);

  uint32_t magic = s.read4();
  expect(t, magic == 0xCAFEBABE);
  unsigned minorVer = s.read2(); // minor version
  unsigned majorVer = s.read2(); // major version
  if(DebugClassReader) {
    fprintf(stderr, "read class (minor %d major %d)\n", minorVer, majorVer);
  }

  object pool = parsePool(t, s);
  PROTECT(t, pool);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  object class_ = makeClass(t,
                            flags,
                            0, // VM flags
                            0, // fixed size
                            0, // array size
                            0, // array dimensions
                            0, // runtime data index
                            0, // object mask
                            referenceName
                            (t, singletonObject(t, pool, name - 1)),
                            0, // source file
                            0, // super
                            0, // interfaces
                            0, // vtable
                            0, // fields
                            0, // methods
                            0, // addendum
                            0, // static table
                            loader,
                            0, // source
                            0);// vtable length
  PROTECT(t, class_);
  
  unsigned super = s.read2();
  if (super) {
    object sc = resolveClass
      (t, loader, referenceName(t, singletonObject(t, pool, super - 1)),
       true, throwType);

    set(t, class_, ClassSuper, sc);

    classVmFlags(t, class_)
      |= (classVmFlags(t, sc)
          & (ReferenceFlag | WeakReferenceFlag | HasFinalizerFlag
             | NeedInitFlag));
  }

  if(DebugClassReader) {
    fprintf(stderr, "  flags %d name %d super %d\n", flags, name, super);
  }
  
  parseInterfaceTable(t, s, class_, pool, throwType);

  parseFieldTable(t, s, class_, pool);

  parseMethodTable(t, s, class_, pool);

  parseAttributeTable(t, s, class_, pool);

  object vtable = classVirtualTable(t, class_);
  unsigned vtableLength = (vtable ? arrayLength(t, vtable) : 0);

  object real = t->m->processor->makeClass
    (t,
     classFlags(t, class_),
     classVmFlags(t, class_),
     classFixedSize(t, class_),
     classArrayElementSize(t, class_),
     classArrayDimensions(t, class_),
     classObjectMask(t, class_),
     className(t, class_),
     classSourceFile(t, class_),
     classSuper(t, class_),
     classInterfaceTable(t, class_),
     classVirtualTable(t, class_),
     classFieldTable(t, class_),
     classMethodTable(t, class_),
     classAddendum(t, class_),
     classStaticTable(t, class_),
     classLoader(t, class_),
     vtableLength);

  PROTECT(t, real);

  t->m->processor->initVtable(t, real);

  updateClassTables(t, real, class_);

  if (root(t, Machine::PoolMap)) {
    object bootstrapClass = hashMapFind
      (t, root(t, Machine::BootstrapClassMap), className(t, class_),
       byteArrayHash, byteArrayEqual);

    hashMapInsert
      (t, root(t, Machine::PoolMap), bootstrapClass ? bootstrapClass : real,
       pool, objectHash);
  }

  return real;
}

uint64_t
runParseClass(Thread* t, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  System::Region* region = reinterpret_cast<System::Region*>(arguments[1]);
  Machine::Type throwType = static_cast<Machine::Type>(arguments[2]);

  return reinterpret_cast<uintptr_t>
    (parseClass(t, loader, region->start(), region->length(), throwType));
}

object
resolveSystemClass(Thread* t, object loader, object spec, bool throw_,
                   Machine::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  object class_ = hashMapFind
    (t, classLoaderMap(t, loader), spec, byteArrayHash, byteArrayEqual);

  if (class_ == 0) {
    PROTECT(t, class_);

    if (classLoaderParent(t, loader)) {
      class_ = resolveSystemClass
        (t, classLoaderParent(t, loader), spec, false);
      if (class_) {
        return class_;
      }
    }

    if (byteArrayBody(t, spec, 0) == '[') {
      class_ = resolveArrayClass(t, loader, spec, throw_, throwType);
    } else {
      THREAD_RUNTIME_ARRAY(t, char, file, byteArrayLength(t, spec) + 6);
      memcpy(RUNTIME_ARRAY_BODY(file),
             &byteArrayBody(t, spec, 0),
             byteArrayLength(t, spec) - 1);
      memcpy(RUNTIME_ARRAY_BODY(file) + byteArrayLength(t, spec) - 1,
             ".class",
             7);

      System::Region* region = static_cast<Finder*>
        (systemClassLoaderFinder(t, loader))->find
        (RUNTIME_ARRAY_BODY(file));

      if (region) {
        if (Verbose) {
          fprintf(stderr, "parsing %s\n", &byteArrayBody(t, spec, 0));
        }

        { THREAD_RESOURCE(t, System::Region*, region, region->dispose());

          uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(loader),
                                    reinterpret_cast<uintptr_t>(region),
                                    static_cast<uintptr_t>(throwType) };

          // parse class file
          class_ = reinterpret_cast<object>
            (runRaw(t, runParseClass, arguments));

          if (UNLIKELY(t->exception)) {
            if (throw_) {
              object e = t->exception;
              t->exception = 0;
              vm::throw_(t, e);
            } else {
              t->exception = 0;
              return 0;
            }
          }
        }

        if (Verbose) {
          fprintf(stderr, "done parsing %s: %p\n",
                  &byteArrayBody(t, spec, 0),
                  class_);
        }

        { const char* source = static_cast<Finder*>
            (systemClassLoaderFinder(t, loader))->sourceUrl
            (RUNTIME_ARRAY_BODY(file));
          
          if (source) {
            unsigned length = strlen(source);
            object array = makeByteArray(t, length + 1);
            memcpy(&byteArrayBody(t, array, 0), source, length);
            array = internByteArray(t, array);
            
            set(t, class_, ClassSource, array);
          }
        }

        object bootstrapClass = hashMapFind
          (t, root(t, Machine::BootstrapClassMap), spec, byteArrayHash,
           byteArrayEqual);

        if (bootstrapClass) {
          PROTECT(t, bootstrapClass);
          
          updateBootstrapClass(t, bootstrapClass, class_);
          class_ = bootstrapClass;
        }
      }
    }

    if (class_) {
      hashMapInsert(t, classLoaderMap(t, loader), spec, class_, byteArrayHash);

      t->m->classpath->updatePackageMap(t, class_);
    } else if (throw_) {
      throwNew(t, throwType, "%s", &byteArrayBody(t, spec, 0));
    }
  }

  return class_;
}

object
findLoadedClass(Thread* t, object loader, object spec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  return classLoaderMap(t, loader) ? hashMapFind
    (t, classLoaderMap(t, loader), spec, byteArrayHash, byteArrayEqual) : 0;
}

object
resolveClass(Thread* t, object loader, object spec, bool throw_,
             Machine::Type throwType)
{
  if (objectClass(t, loader) == type(t, Machine::SystemClassLoaderType)) {
    return resolveSystemClass(t, loader, spec, throw_, throwType);
  } else {
    PROTECT(t, loader);
    PROTECT(t, spec);

    object c = findLoadedClass(t, loader, spec);
    if (c) {
      return c;
    }

    if (byteArrayBody(t, spec, 0) == '[') {
      c = resolveArrayClass(t, loader, spec, throw_, throwType);
    } else {
      if (root(t, Machine::LoadClassMethod) == 0) {
        object m = resolveMethod
          (t, root(t, Machine::BootLoader), "java/lang/ClassLoader",
           "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

        if (m) {
          setRoot(t, Machine::LoadClassMethod, m);

          object classLoaderClass = type(t, Machine::ClassLoaderType);
        
          if (classVmFlags(t, classLoaderClass) & BootstrapFlag) {
            resolveSystemClass
              (t, root(t, Machine::BootLoader),
               vm::className(t, classLoaderClass));
          }
        }      
      }

      object method = findVirtualMethod
        (t, root(t, Machine::LoadClassMethod), objectClass(t, loader));

      PROTECT(t, method);
        
      THREAD_RUNTIME_ARRAY(t, char, s, byteArrayLength(t, spec));
      replace('/', '.', RUNTIME_ARRAY_BODY(s), reinterpret_cast<char*>
              (&byteArrayBody(t, spec, 0)));

      object specString = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));
      PROTECT(t, specString);

      uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(method),
                                reinterpret_cast<uintptr_t>(loader),
                                reinterpret_cast<uintptr_t>(specString) };

      object jc = reinterpret_cast<object>
        (runRaw(t, invokeLoadClass, arguments));

      if (LIKELY(jc)) {
        c = jclassVmClass(t, jc);
      } else if (t->exception) {
        if (throw_) {
          object e = type(t, throwType) == objectClass(t, t->exception)
            ? t->exception
            : makeThrowable(t, throwType, specString, 0, t->exception);
          t->exception = 0;
          vm::throw_(t, e);
        } else {
          t->exception = 0;
        }
      }
    }

    if (LIKELY(c)) {
      PROTECT(t, c);

      saveLoadedClass(t, loader, c);
    } else if (throw_) {
      throwNew(t, throwType, "%s", &byteArrayBody(t, spec, 0));
    }

    return c;
  }
}

object
resolveMethod(Thread* t, object class_, const char* methodName,
              const char* methodSpec)
{
  PROTECT(t, class_);

  object name = makeByteArray(t, methodName);
  PROTECT(t, name);

  object spec = makeByteArray(t, methodSpec);
    
  object method = findMethodInClass(t, class_, name, spec);

  if (method == 0) {
    throwNew(t, Machine::NoSuchMethodErrorType, "%s %s not found in %s",
             methodName, methodSpec, &byteArrayBody
             (t, className(t, class_), 0));
  } else {
    return method;
  }
}

object
resolveField(Thread* t, object class_, const char* fieldName,
              const char* fieldSpec)
{
  PROTECT(t, class_);

  object name = makeByteArray(t, fieldName);
  PROTECT(t, name);

  object spec = makeByteArray(t, fieldSpec);
  PROTECT(t, spec);
 
  object field = findInInterfaces(t, class_, name, spec, findFieldInClass);

  object c = class_;
  PROTECT(t, c);

  for (; c != 0 and field == 0; c = classSuper(t, c)) {
    field = findFieldInClass(t, c, name, spec);
  }

  if (field == 0) {
    throwNew(t, Machine::NoSuchFieldErrorType, "%s %s not found in %s",
             fieldName, fieldSpec, &byteArrayBody(t, className(t, class_), 0));
  } else {
    return field;
  }
}

bool
classNeedsInit(Thread* t, object c)
{
  if (classVmFlags(t, c) & NeedInitFlag) {
    if (classVmFlags(t, c) & InitFlag) {
      // the class is currently being initialized.  If this the thread
      // which is initializing it, we should not try to initialize it
      // recursively.  Otherwise, we must wait for the responsible
      // thread to finish.
      for (Thread::ClassInitStack* s = t->classInitStack; s; s = s->next) {
        if (s->class_ == c) {
          return false;
        }
      }
    }
    return true;
  } else {
    return false;
  }
}

bool
preInitClass(Thread* t, object c)
{
  int flags = classVmFlags(t, c);

  loadMemoryBarrier();

  if (flags & NeedInitFlag) {
    PROTECT(t, c);
    ACQUIRE(t, t->m->classLock);

    if (classVmFlags(t, c) & NeedInitFlag) {
      if (classVmFlags(t, c) & InitFlag) {
        // If the class is currently being initialized and this the thread
        // which is initializing it, we should not try to initialize it
        // recursively.
        if (isInitializing(t, c)) {
          return false;
        }

        // some other thread is on the job - wait for it to finish.
        while (classVmFlags(t, c) & InitFlag) {
          ENTER(t, Thread::IdleState);
          t->m->classLock->wait(t->systemThread, 0);
        }
      } else if (classVmFlags(t, c) & InitErrorFlag) {
        throwNew(t, Machine::NoClassDefFoundErrorType, "%s",
                 &byteArrayBody(t, className(t, c), 0));
      } else {
        classVmFlags(t, c) |= InitFlag;
        return true;
      }
    }
  }
  return false;
}

void
postInitClass(Thread* t, object c)
{
  PROTECT(t, c);
  ACQUIRE(t, t->m->classLock);

  if (t->exception) {
    classVmFlags(t, c) |= NeedInitFlag | InitErrorFlag;
    classVmFlags(t, c) &= ~InitFlag;

    object exception = t->exception;
    t->exception = 0;

    throwNew(t, Machine::ExceptionInInitializerErrorType,
             static_cast<object>(0), 0, exception);
  } else {
    classVmFlags(t, c) &= ~(NeedInitFlag | InitFlag);
  }
  t->m->classLock->notifyAll(t->systemThread);
}

void
initClass(Thread* t, object c)
{
  PROTECT(t, c);

  object super = classSuper(t, c);
  if (super) {
    initClass(t, super);
  }

  if (preInitClass(t, c)) {
    OBJECT_RESOURCE(t, c, postInitClass(t, c));

    object initializer = classInitializer(t, c);

    if (initializer) {
      Thread::ClassInitStack stack(t, c);

      t->m->processor->invoke(t, initializer, 0);
    }
  }
}

object
resolveObjectArrayClass(Thread* t, object loader, object elementClass)
{
  PROTECT(t, loader);
  PROTECT(t, elementClass);

  { object arrayClass = classRuntimeDataArrayClass
      (t, getClassRuntimeData(t, elementClass));
    if (arrayClass) {
      return arrayClass;
    }
  }

  object elementSpec = className(t, elementClass);
  PROTECT(t, elementSpec);

  object spec;
  if (byteArrayBody(t, elementSpec, 0) == '[') {
    spec = makeByteArray(t, byteArrayLength(t, elementSpec) + 1);
    byteArrayBody(t, spec, 0) = '[';
    memcpy(&byteArrayBody(t, spec, 1),
           &byteArrayBody(t, elementSpec, 0),
           byteArrayLength(t, elementSpec));
  } else {
    spec = makeByteArray(t, byteArrayLength(t, elementSpec) + 3);
    byteArrayBody(t, spec, 0) = '[';
    byteArrayBody(t, spec, 1) = 'L';
    memcpy(&byteArrayBody(t, spec, 2),
           &byteArrayBody(t, elementSpec, 0),
           byteArrayLength(t, elementSpec) - 1);
    byteArrayBody(t, spec, byteArrayLength(t, elementSpec) + 1) = ';';
    byteArrayBody(t, spec, byteArrayLength(t, elementSpec) + 2) = 0;
  }

  object arrayClass = resolveClass(t, loader, spec);

  set(t, getClassRuntimeData(t, elementClass), ClassRuntimeDataArrayClass,
      arrayClass);

  return arrayClass;
}

object
makeObjectArray(Thread* t, object elementClass, unsigned count)
{
  object arrayClass = resolveObjectArrayClass
    (t, classLoader(t, elementClass), elementClass);

  PROTECT(t, arrayClass);

  object array = makeArray(t, count);
  setObjectClass(t, array, arrayClass);

  return array;
}

object
findFieldInClass(Thread* t, object class_, object name, object spec)
{
  return findInTable
    (t, classFieldTable(t, class_), name, spec, fieldName, fieldSpec);
}

object
findMethodInClass(Thread* t, object class_, object name, object spec)
{
  return findInTable
    (t, classMethodTable(t, class_), name, spec, methodName, methodSpec);
}

object
findInHierarchyOrNull(Thread* t, object class_, object name, object spec,
                      object (*find)(Thread*, object, object, object))
{
  object originalClass = class_;

  object o = 0;
  if ((classFlags(t, class_) & ACC_INTERFACE)
      and classVirtualTable(t, class_))
  {
    o = findInTable
      (t, classVirtualTable(t, class_), name, spec, methodName, methodSpec);
  }

  if (o == 0) {
    for (; o == 0 and class_; class_ = classSuper(t, class_)) {
      o = find(t, class_, name, spec);
    }

    if (o == 0 and find == findFieldInClass) {
      o = findInInterfaces(t, originalClass, name, spec, find);
    }
  }

  return o;
}

unsigned
parameterFootprint(Thread* t, const char* s, bool static_)
{
  unsigned footprint = 0;
  for (MethodSpecIterator it(t, s); it.hasNext();) {
    switch (*it.next()) {
    case 'J':
    case 'D':
      footprint += 2;
      break;

    default:
      ++ footprint;
      break;        
    }
  }

  if (not static_) {
    ++ footprint;
  }
  return footprint;
}

void
addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object))
{
  PROTECT(t, target);

  ACQUIRE(t, t->m->referenceLock);

  void* function;
  memcpy(&function, &finalize, BytesPerWord);

  object f = makeFinalizer(t, 0, function, 0, 0, 0);
  finalizerTarget(t, f) = target;
  finalizerNext(t, f) = t->m->finalizers;
  t->m->finalizers = f;
}

object
objectMonitor(Thread* t, object o, bool createNew)
{
  assert(t, t->state == Thread::ActiveState);

  object m = hashMapFind
    (t, root(t, Machine::MonitorMap), o, objectHash, objectEqual);

  if (m) {
    if (DebugMonitors) {
      fprintf(stderr, "found monitor %p for object %x\n", m, objectHash(t, o));
    }

    return m;
  } else if (createNew) {
    PROTECT(t, o);
    PROTECT(t, m);

    { ENTER(t, Thread::ExclusiveState);

      m = hashMapFind
        (t, root(t, Machine::MonitorMap), o, objectHash, objectEqual);

      if (m) {
        if (DebugMonitors) {
          fprintf(stderr, "found monitor %p for object %x\n",
                  m, objectHash(t, o));
        }

        return m;
      }

      object head = makeMonitorNode(t, 0, 0);
      m = makeMonitor(t, 0, 0, 0, head, head, 0);

      if (DebugMonitors) {
        fprintf(stderr, "made monitor %p for object %x\n", m,
                objectHash(t, o));
      }

      hashMapInsert(t, root(t, Machine::MonitorMap), o, m, objectHash);

      addFinalizer(t, o, removeMonitor);
    }

    return m;
  } else {
    return 0;
  }
}

object
intern(Thread* t, object s)
{
  PROTECT(t, s);

  ACQUIRE(t, t->m->referenceLock);

  object n = hashMapFindNode
    (t, root(t, Machine::StringMap), s, stringHash, stringEqual);

  if (n) {
    return jreferenceTarget(t, tripleFirst(t, n));
  } else {
    hashMapInsert(t, root(t, Machine::StringMap), s, 0, stringHash);
    addFinalizer(t, s, removeString);
    return s;
  }
}

void
walk(Thread* t, Heap::Walker* w, object o, unsigned start)
{
  object class_ = static_cast<object>(t->m->heap->follow(objectClass(t, o)));
  object objectMask = static_cast<object>
    (t->m->heap->follow(classObjectMask(t, class_)));

  bool more = true;

  if (objectMask) {
    unsigned fixedSize = classFixedSize(t, class_);
    unsigned arrayElementSize = classArrayElementSize(t, class_);
    unsigned arrayLength
      = (arrayElementSize ?
         fieldAtOffset<uintptr_t>(o, fixedSize - BytesPerWord) : 0);

    THREAD_RUNTIME_ARRAY(t, uint32_t, mask, intArrayLength(t, objectMask));
    memcpy(RUNTIME_ARRAY_BODY(mask), &intArrayBody(t, objectMask, 0),
           intArrayLength(t, objectMask) * 4);

    more = ::walk(t, w, RUNTIME_ARRAY_BODY(mask), fixedSize, arrayElementSize,
                  arrayLength, start);
  } else if (classVmFlags(t, class_) & SingletonFlag) {
    unsigned length = singletonLength(t, o);
    if (length) {
      more = ::walk(t, w, singletonMask(t, o),
                    (singletonCount(t, o) + 2) * BytesPerWord, 0, 0, start);
    } else if (start == 0) {
      more = w->visit(0);
    }
  } else if (start == 0) {
    more = w->visit(0);
  }

  if (more and classVmFlags(t, class_) & ContinuationFlag) {
    t->m->processor->walkContinuationBody(t, w, o, start);
  }
}

int
walkNext(Thread* t, object o, int previous)
{
  class Walker: public Heap::Walker {
   public:
    Walker(): value(-1) { }

    bool visit(unsigned offset) {
      value = offset;
      return false;
    }

    int value;
  } walker;

  walk(t, &walker, o, previous + 1);
  return walker.value;
}

void
visitRoots(Machine* m, Heap::Visitor* v)
{
  v->visit(&(m->types));
  v->visit(&(m->roots));

  for (Thread* t = m->rootThread; t; t = t->peer) {
    ::visitRoots(t, v);
  }

  for (Reference* r = m->jniReferences; r; r = r->next) {
    if (not r->weak) {
      v->visit(&(r->target));
    }
  }
}

void
logTrace(FILE* f, const char* fmt, ...)
{
    va_list a;
    va_start(a, fmt);
#ifdef PLATFORM_WINDOWS
    const unsigned length = _vscprintf(fmt, a);
#else
    const unsigned length = vsnprintf(0, 0, fmt, a);
#endif
    va_end(a);

    RUNTIME_ARRAY(char, buffer, length + 1);
    va_start(a, fmt);
    vsnprintf(RUNTIME_ARRAY_BODY(buffer), length + 1, fmt, a);
    va_end(a);
    RUNTIME_ARRAY_BODY(buffer)[length] = 0;

    ::fprintf(f, "%s", RUNTIME_ARRAY_BODY(buffer));
#ifdef PLATFORM_WINDOWS
    ::OutputDebugStringA(RUNTIME_ARRAY_BODY(buffer));
#endif
}

void
printTrace(Thread* t, object exception)
{
  if (exception == 0) {
    exception = makeThrowable(t, Machine::NullPointerExceptionType);
  }

  for (object e = exception; e; e = throwableCause(t, e)) {
    if (e != exception) {
      logTrace(errorLog(t), "caused by: ");
    }

    logTrace(errorLog(t), "%s", &byteArrayBody
            (t, className(t, objectClass(t, e)), 0));

    if (throwableMessage(t, e)) {
      object m = throwableMessage(t, e);
      THREAD_RUNTIME_ARRAY(t, char, message, stringLength(t, m) + 1);
      stringChars(t, m, RUNTIME_ARRAY_BODY(message));
      logTrace(errorLog(t), ": %s\n", RUNTIME_ARRAY_BODY(message));
    } else {
      logTrace(errorLog(t), "\n");
    }

    object trace = throwableTrace(t, e);
    if (trace) {
      for (unsigned i = 0; i < objectArrayLength(t, trace); ++i) {
        object e = objectArrayBody(t, trace, i);
        const int8_t* class_ = &byteArrayBody
          (t, className(t, methodClass(t, traceElementMethod(t, e))), 0);
        const int8_t* method = &byteArrayBody
          (t, methodName(t, traceElementMethod(t, e)), 0);
        int line = t->m->processor->lineNumber
          (t, traceElementMethod(t, e), traceElementIp(t, e));

        logTrace(errorLog(t), "  at %s.%s ", class_, method);

        switch (line) {
        case NativeLine:
          logTrace(errorLog(t), "(native)\n");
          break;
        case UnknownLine:
          logTrace(errorLog(t), "(unknown line)\n");
          break;
        default:
          logTrace(errorLog(t), "(line %d)\n", line);
        }
      }
    }

    if (e == throwableCause(t, e)) {
      break;
    }
  }

  ::fflush(errorLog(t));
}

object
makeTrace(Thread* t, Processor::StackWalker* walker)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t), trace(0), index(0), protector(t, &trace) { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (trace == 0) {
        trace = makeObjectArray(t, walker->count());
        assert(t, trace);
      }

      object e = makeTraceElement(t, walker->method(), walker->ip());
      assert(t, index < objectArrayLength(t, trace));
      set(t, trace, ArrayBody + (index * BytesPerWord), e);
      ++ index;
      return true;
    }

    Thread* t;
    object trace;
    unsigned index;
    Thread::SingleProtector protector;
  } v(t);

  walker->walk(&v);

  return v.trace ? v.trace : makeObjectArray(t, 0);
}

object
makeTrace(Thread* t, Thread* target)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t), trace(0) { }

    virtual bool visit(Processor::StackWalker* walker) {
      trace = vm::makeTrace(t, walker);
      return false;
    }

    Thread* t;
    object trace;
  } v(t);

  t->m->processor->walkStack(target, &v);

  return v.trace ? v.trace : makeObjectArray(t, 0);
}

void
runFinalizeThread(Thread* t)
{
  object finalizeList = 0;
  PROTECT(t, finalizeList);

  object cleanList = 0;
  PROTECT(t, cleanList);

  while (true) {
    { ACQUIRE(t, t->m->stateLock);

      while (t->m->finalizeThread
             and root(t, Machine::ObjectsToFinalize) == 0
             and root(t, Machine::ObjectsToClean) == 0)
      {
        ENTER(t, Thread::IdleState);
        t->m->stateLock->wait(t->systemThread, 0);
      }

      if (t->m->finalizeThread == 0) {
        return;
      } else {
        finalizeList = root(t, Machine::ObjectsToFinalize);
        setRoot(t, Machine::ObjectsToFinalize, 0);

        cleanList = root(t, Machine::ObjectsToClean);
        setRoot(t, Machine::ObjectsToClean, 0);
      }
    }

    for (; finalizeList; finalizeList = finalizerQueueNext(t, finalizeList)) {
      finalizeObject(t, finalizerQueueTarget(t, finalizeList), "finalize");
    }

    for (; cleanList; cleanList = cleanerQueueNext(t, cleanList)) {
      finalizeObject(t, cleanList, "clean");
    }
  }
}

object
parseUtf8(Thread* t, const char* data, unsigned length)
{
  class Client: public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void handleError() {
      //      vm::abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, reinterpret_cast<const uint8_t*>(data), length);

  return ::parseUtf8(t, s, length);
}

object
parseUtf8(Thread* t, object array)
{
  for (unsigned i = 0; i < byteArrayLength(t, array) - 1; ++i) {
    if (byteArrayBody(t, array, i) & 0x80) {
      goto slow_path;
    }
  }

  return array;

 slow_path:
  class Client: public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void handleError() {
      //      vm::abort(t);
    }

   private:
    Thread* t;
  } client(t);

  class MyStream: public AbstractStream {
   public:
    class MyProtector: public Thread::Protector {
     public:
      MyProtector(Thread* t, MyStream* s):
        Protector(t), s(s)
      { }

      virtual void visit(Heap::Visitor* v) {
        v->visit(&(s->array));
      }

      MyStream* s;
    };

    MyStream(Thread* t, Client* client, object array):
      AbstractStream(client, byteArrayLength(t, array) - 1),
      array(array),
      protector(t, this)
    { }

    virtual void copy(uint8_t* dst, unsigned offset, unsigned size) {
      memcpy(dst, &byteArrayBody(protector.t, array, offset), size);
    }

    object array;
    MyProtector protector;
  } s(t, &client, array);

  return ::parseUtf8(t, s, byteArrayLength(t, array) - 1);
}

object
getCaller(Thread* t, unsigned target, bool skipMethodInvoke)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, unsigned target, bool skipMethodInvoke):
      t(t), method(0), count(0), target(target),
      skipMethodInvoke(skipMethodInvoke)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipMethodInvoke
          and methodClass
          (t, walker->method()) == type(t, Machine::JmethodType)
          and strcmp
          (&byteArrayBody(t, methodName(t, walker->method()), 0),
           reinterpret_cast<const int8_t*>("invoke"))
          == 0)
      {
        return true;
      }

      if (count == target) {
        method = walker->method();
        return false;
      } else {
        ++ count;
        return true;
      }
    }

    Thread* t;
    object method;
    unsigned count;
    unsigned target;
    bool skipMethodInvoke;
    } v(t, target, skipMethodInvoke);

  t->m->processor->walkStack(t, &v);

  return v.method;
}

object
defineClass(Thread* t, object loader, const uint8_t* buffer, unsigned length)
{
  PROTECT(t, loader);

  object c = parseClass(t, loader, buffer, length);
  
  // char name[byteArrayLength(t, className(t, c))];
  // memcpy(name, &byteArrayBody(t, className(t, c), 0),
  //        byteArrayLength(t, className(t, c)));
  // replace('/', '-', name);

  // const unsigned BufferSize = 1024;
  // char path[BufferSize];
  // snprintf(path, BufferSize, "/tmp/avian-define-class/%s.class", name);

  // FILE* file = fopen(path, "wb");
  // if (file) {
  //   fwrite(buffer, length, 1, file);
  //   fclose(file);
  // }

  PROTECT(t, c);

  saveLoadedClass(t, loader, c);

  return c;
}

void
populateMultiArray(Thread* t, object array, int32_t* counts,
                   unsigned index, unsigned dimensions)
{
  if (index + 1 == dimensions or counts[index] == 0) {
    return;
  }

  PROTECT(t, array);

  object spec = className(t, objectClass(t, array));
  PROTECT(t, spec);

  object elementSpec = makeByteArray(t, byteArrayLength(t, spec) - 1);
  memcpy(&byteArrayBody(t, elementSpec, 0),
         &byteArrayBody(t, spec, 1),
         byteArrayLength(t, spec) - 1);

  object class_ = resolveClass
    (t, classLoader(t,  objectClass(t, array)), elementSpec);
  PROTECT(t, class_);

  for (int32_t i = 0; i < counts[index]; ++i) {
    object a = makeArray
      (t, ceilingDivide
       (counts[index + 1] * classArrayElementSize(t, class_), BytesPerWord));
    arrayLength(t, a) = counts[index + 1];
    setObjectClass(t, a, class_);
    set(t, array, ArrayBody + (i * BytesPerWord), a);
    
    populateMultiArray(t, a, counts, index + 1, dimensions);
  }
}

void
noop()
{ }

#include "type-constructors.cpp"

} // namespace vm

// for debugging
JNIEXPORT void
vmfPrintTrace(Thread* t, FILE* out)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, FILE* out): t(t), out(out) { }

    virtual bool visit(Processor::StackWalker* walker) {
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, walker->method())), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, walker->method()), 0);
      int line = t->m->processor->lineNumber
        (t, walker->method(), walker->ip());

      fprintf(out, "  at %s.%s ", class_, method);

      switch (line) {
      case NativeLine:
        fprintf(out, "(native)\n");
        break;
      case UnknownLine:
        fprintf(out, "(unknown line)\n");
        break;
      default:
        fprintf(out, "(line %d)\n", line);
      }

      return true;
    }

    Thread* t;
    FILE* out;
  } v(t, out);

  fprintf(out, "debug trace for thread %p\n", t);

  t->m->processor->walkStack(t, &v);

  fflush(out);
}

JNIEXPORT void
vmPrintTrace(Thread* t)
{
  vmfPrintTrace(t, stderr);
}

// also for debugging
JNIEXPORT void*
vmAddressFromLine(Thread* t, object m, unsigned line)
{
  object code = methodCode(t, m);
  printf("code: %p\n", code);
  object lnt = codeLineNumberTable(t, code);
  printf("lnt: %p\n", lnt);
	
  if (lnt) {
    unsigned last = 0;
    unsigned bottom = 0;
    unsigned top = lineNumberTableLength(t, lnt);
    for(unsigned i = bottom; i < top; i++)
    {
      uint64_t ln = lineNumberTableBody(t, lnt, i);
      if(lineNumberLine(ln) == line)
        return reinterpret_cast<void*>(lineNumberIp(ln));
      else if(lineNumberLine(ln) > line)
        return reinterpret_cast<void*>(last);
      last = lineNumberIp(ln);
    }
  }
  return 0;
}
