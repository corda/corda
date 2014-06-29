/* Copyright (c) 2008-2014, Avian Contributors

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
    assertT(t, o->state != Thread::JoinedState);
    assertT(t, (o->flags & Thread::SystemFlag) == 0);
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

  { GcFinalizer* p = 0;
    PROTECT(t, p);

    for (p = t->m->finalizers; p;) {
      GcFinalizer* f = p;
      p = cast<GcFinalizer>(t, p->next());

      void (*function)(Thread*, object);
      memcpy(&function, &f->finalize(), BytesPerWord);
      if (function) {
        function(t, f->target());
      }
    }

    for (p = t->m->tenuredFinalizers; p;) {
      GcFinalizer* f = p;
      p = cast<GcFinalizer>(t, p->next());

      void (*function)(Thread*, object);
      memcpy(&function, &f->finalize(), BytesPerWord);
      if (function) {
        function(t, f->target());
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
       p; p = reinterpret_cast<object>(cast<GcFinder>(t, p)->next()))
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
findInInterfaces(Thread* t, GcClass* class_, GcByteArray* name, GcByteArray* spec,
                 object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*))
{
  object result = 0;
  if (class_->interfaceTable()) {
    for (unsigned i = 0;
         i < arrayLength(t, class_->interfaceTable()) and result == 0;
         i += 2)
    {
      result = find
        (t, cast<GcClass>(t, arrayBody(t, class_->interfaceTable(), i)), name, spec);
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
    finalizerNext(t, finalizer) = reinterpret_cast<object>(t->m->finalizeQueue);
    t->m->finalizeQueue = cast<GcFinalizer>(t, finalizer);
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

  if (objectClass(t, *p) == type(t, GcCleaner::Type)) {
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

  assertT(t, m->finalizeQueue == 0);

  m->heap->postVisit();

  for (object p = reinterpret_cast<object>(m->weakReferences); p;) {
    object r = static_cast<object>(m->heap->follow(p));
    p = jreferenceVmNext(t, r);
    clearTargetIfFinalizable(t, r);
  }

  if (major) {
    for (object p = reinterpret_cast<object>(m->tenuredWeakReferences); p;) {
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
    for (GcFinalizer** p = &(m->finalizers); *p;) {
      v->visit(p);

      if (m->heap->status((*p)->target()) == Heap::Unreachable) {
        GcFinalizer* finalizer = *p;
        *p = cast<GcFinalizer>(t, finalizer->next());

        finalizer->next() = unreachable;
        unreachable = reinterpret_cast<object>(finalizer);
      } else {
        p = reinterpret_cast<GcFinalizer**>(&(*p)->next());
      }
    }

    for (GcFinalizer** p = &(m->finalizers); *p;) {
      // target is reachable
      v->visit(&(*p)->target());

      if (m->heap->status(*p) == Heap::Tenured) {
        // the finalizer is tenured, so we remove it from
        // m->finalizers and later add it to m->tenuredFinalizers

        if (lastNewTenuredFinalizer == 0) {
          lastNewTenuredFinalizer = reinterpret_cast<object>(*p);
        }

        GcFinalizer* finalizer = *p;
        *p = cast<GcFinalizer>(t, finalizer->next());
        finalizer->next() = firstNewTenuredFinalizer;
        firstNewTenuredFinalizer = reinterpret_cast<object>(finalizer);
      } else {
        p = reinterpret_cast<GcFinalizer**>(&(*p)->next());
      }
    }

    for (object* p = &unreachable; *p;) {
      // target is unreachable - queue it up for finalization
      finalizerTargetUnreachable(t, v, p);
    }
  }

  object firstNewTenuredWeakReference = 0;
  object lastNewTenuredWeakReference = 0;

  for (object* p = reinterpret_cast<object*>(&(m->weakReferences)); *p;) {
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
      for (GcFinalizer** p = &(m->tenuredFinalizers); *p;) {
        v->visit(p);

        if (m->heap->status((*p)->target()) == Heap::Unreachable) {
          GcFinalizer* finalizer = *p;
          *p = cast<GcFinalizer>(t, finalizer->next());

          finalizer->next() = unreachable;
          unreachable = reinterpret_cast<object>(finalizer);
        } else {
          p = reinterpret_cast<GcFinalizer**>(&(*p)->next());
        }
      }

      for (GcFinalizer** p = &(m->tenuredFinalizers); *p;) {
        // target is reachable
        v->visit(&(*p)->target());
        p = reinterpret_cast<GcFinalizer**>(&(*p)->next());
      }

      for (object* p = &unreachable; *p;) {
        // target is unreachable - queue it up for finalization
        finalizerTargetUnreachable(t, v, p);
      }
    }

    for (object* p = reinterpret_cast<object*>(&m->tenuredWeakReferences); *p;) {
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
    finalizerNext(t, lastNewTenuredFinalizer) = reinterpret_cast<object>(m->tenuredFinalizers);
    m->tenuredFinalizers = cast<GcFinalizer>(t, firstNewTenuredFinalizer);
  }

  if (lastNewTenuredWeakReference) {
    jreferenceVmNext(t, lastNewTenuredWeakReference)
      = reinterpret_cast<object>(m->tenuredWeakReferences);
    m->tenuredWeakReferences = cast<GcJreference>(t, firstNewTenuredWeakReference);
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
  GcMethod* m = cast<GcMethod>(t, *reinterpret_cast<object*>(arguments[0]));
  object o = *reinterpret_cast<object*>(arguments[1]);

  t->m->processor->invoke(t, m, o);

  return 1;
}

void
finalizeObject(Thread* t, object o, const char* name)
{
  for (GcClass* c = objectClass(t, o); c; c = c->super()) {
    for (unsigned i = 0; i < arrayLength(t, c->methodTable()); ++i) {
      object m = arrayBody(t, c->methodTable(), i);

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
  object value = reinterpret_cast<object>(makeCharArray(t, length + 1));

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
  assertT(t, si < length);
        unsigned b = readByte(s, &byteB);
  unsigned c = s.read1();
  charArrayBody(t, value, vi++)
          = ((a & 0xf) << 12) | ((b & 0x3f) << 6) | (c & 0x3f);
      } else {
  // 2 bytes
  ++ si;
  assertT(t, si < length);
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
    
    object v = reinterpret_cast<object>(makeCharArray(t, vi + 1));
    memcpy(&charArrayBody(t, v, 0), &charArrayBody(t, value, 0), vi * 2);
    value = v;
  }
  
  return value;
}

object
parseUtf8(Thread* t, AbstractStream& s, unsigned length)
{
  object value = reinterpret_cast<object>(makeByteArray(t, length + 1));
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
          assertT(t, si < length);
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
    
    object v = reinterpret_cast<object>(makeByteArray(t, vi + 1));
    memcpy(&byteArrayBody(t, v, 0), &byteArrayBody(t, value, 0), vi);
    value = v;
  }
  
  return value;
}

object
makeByteArray(Thread* t, Stream& s, unsigned length)
{
  object value = reinterpret_cast<object>(makeByteArray(t, length + 1));
  s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, value, 0)), length);
  return value;
}

void
removeByteArray(Thread* t, object o)
{
  hashMapRemove(t,
                cast<GcHashMap>(t, root(t, Machine::ByteArrayMap)),
                o,
                byteArrayHash,
                objectEqual);
}

object
internByteArray(Thread* t, object array)
{
  PROTECT(t, array);

  ACQUIRE(t, t->m->referenceLock);

  GcTriple* n = hashMapFindNode
    (t, cast<GcHashMap>(t, root(t, Machine::ByteArrayMap)), array, byteArrayHash, byteArrayEqual);
  if (n) {
    return jreferenceTarget(t, n->first());
  } else {
    hashMapInsert(t, cast<GcHashMap>(t, root(t, Machine::ByteArrayMap)), array, 0, byteArrayHash);
    addFinalizer(t, array, removeByteArray);
    return array;
  }
}

unsigned
parsePoolEntry(Thread* t, Stream& s, uint32_t* index, GcSingleton* pool, unsigned i)
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
      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = utf8 %s\n", i, &byteArrayBody(t, value, 0));
      }
    }
  } return 1;

  case CONSTANT_Class: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = reinterpret_cast<object>(makeReference(t, 0, 0, cast<GcByteArray>(t, singletonObject(t, pool, si)), 0));
      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = class <todo>\n", i);
      }
    }
  } return 1;

  case CONSTANT_String: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = parseUtf8(t, cast<GcByteArray>(t, singletonObject(t, pool, si)));
      value = reinterpret_cast<object>(t->m->classpath->makeString
        (t, value, 0, fieldAtOffset<uintptr_t>(value, BytesPerWord) - 1));
      value = intern(t, value);
      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);

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
      object value = reinterpret_cast<object>(makePair(t, name, type));
      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);

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

      GcByteArray* className = referenceName(t, singletonObject(t, pool, ci));
      object nameAndType = singletonObject(t, pool, nti);

      object value = reinterpret_cast<object>(makeReference
        (t, 0, className, cast<GcByteArray>(t, pairFirst(t, nameAndType)), cast<GcByteArray>(t, pairSecond(t, nameAndType))));
      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);

      if(DebugClassReader) {
        fprintf(stderr, "    consts[%d] = method %s.%s%s\n", i, className->body().begin(), &byteArrayBody(t, pairFirst(t, nameAndType), 0), &byteArrayBody(t, pairSecond(t, nameAndType), 0));
      }
    }
  } return 1;

  case CONSTANT_MethodHandle:
    if (singletonObject(t, pool, i) == 0) {
      unsigned kind = s.read1();
      unsigned ri = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ri);

      object value = singletonObject(t, pool, ri);

      if (DebugClassReader) {
        fprintf(stderr, "   consts[%d] = method handle %d %s.%s%s\n", i, kind,
                referenceClass(t, value)->body().begin(),
                referenceName(t, value)->body().begin(),
                referenceSpec(t, value)->body().begin());
      }

      value = reinterpret_cast<object>(makeReference
        (t, kind, referenceClass(t, value), referenceName(t, value),
         referenceSpec(t, value)));

      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);
    } return 1;

  case CONSTANT_MethodType:
    if (singletonObject(t, pool, i) == 0) {
      unsigned ni = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ni);

      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord),
          singletonObject(t, pool, ni));
    } return 1;

  case CONSTANT_InvokeDynamic:
    if (singletonObject(t, pool, i) == 0) {
      unsigned bootstrap = s.read2();
      unsigned nti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, nti);

      object nameAndType = singletonObject(t, pool, nti);

      const char* specString = reinterpret_cast<const char*>
        (&byteArrayBody(t, pairSecond(t, nameAndType), 0));

      unsigned parameterCount;
      unsigned parameterFootprint;
      unsigned returnCode;
      scanMethodSpec
        (t, specString, true, &parameterCount, &parameterFootprint,
         &returnCode);

      GcMethod* template_ = makeMethod
        (t, 0, returnCode, parameterCount, parameterFootprint, 0, 0, 0, 0,
         cast<GcByteArray>(t, pairFirst(t, nameAndType)), cast<GcByteArray>(t, pairSecond(t, nameAndType)), 0, 0, 0);

      object value = reinterpret_cast
          <object>(makeInvocation(t,
                                  bootstrap,
                                  -1,
                                  0,
                                  reinterpret_cast<object>(pool),
                                  reinterpret_cast<object>(template_),
                                  0));

      set(t, reinterpret_cast<object>(pool), SingletonBody + (i * BytesPerWord), value);
    } return 1;

  default: abort(t);
  }
}

GcSingleton*
parsePool(Thread* t, Stream& s)
{
  unsigned count = s.read2() - 1;
  GcSingleton* pool = makeSingletonOfSize(t, count + poolMaskSize(count));
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

      case CONSTANT_MethodHandle:
        singletonMarkObject(t, pool, i);
        s.skip(3);
        break;

      case CONSTANT_MethodType:
        singletonMarkObject(t, pool, i);
        s.skip(2);
        break;

      case CONSTANT_InvokeDynamic:
        singletonMarkObject(t, pool, i);
        s.skip(4);
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
addInterfaces(Thread* t, GcClass* class_, GcHashMap* map)
{
  object table = class_->interfaceTable();
  if (table) {
    unsigned increment = 2;
    if (class_->flags() & ACC_INTERFACE) {
      increment = 1;
    }

    PROTECT(t, map);
    PROTECT(t, table);

    for (unsigned i = 0; i < arrayLength(t, table); i += increment) {
      GcClass* interface = cast<GcClass>(t, arrayBody(t, table, i));
      GcByteArray* name = interface->name();
      hashMapInsertMaybe(t,
                         map,
                         reinterpret_cast<object>(name),
                         reinterpret_cast<object>(interface),
                         byteArrayHash,
                         byteArrayEqual);
    }
  }
}

GcClassAddendum*
getClassAddendum(Thread* t, GcClass* class_, GcSingleton* pool)
{
  GcClassAddendum* addendum = class_->addendum();
  if (addendum == 0) {
    PROTECT(t, class_);

    addendum = makeClassAddendum(t, pool, 0, 0, 0, 0, -1, 0, 0);
    set(t,
        reinterpret_cast<object>(class_),
        ClassAddendum,
        reinterpret_cast<object>(addendum));
  }
  return addendum;
}

void
parseInterfaceTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool,
                    Gc::Type throwType)
{
  PROTECT(t, class_);
  PROTECT(t, pool);
  
  GcHashMap* map = makeHashMap(t, 0, 0);
  PROTECT(t, map);

  if (class_->super()) {
    addInterfaces(t, class_->super(), map);
  }

  unsigned count = s.read2();
  object table = 0;
  PROTECT(t, table);

  if (count) {
    table = reinterpret_cast<object>(makeArray(t, count));

    object addendum = reinterpret_cast
        <object>(getClassAddendum(t, class_, pool));
    set(t, addendum, ClassAddendumInterfaceTable, table);
  }

  for (unsigned i = 0; i < count; ++i) {
    GcByteArray* name = referenceName(t, singletonObject(t, pool, s.read2() - 1));
    PROTECT(t, name);

    GcClass* interface = resolveClass
      (t, class_->loader(), name, true, throwType);

    PROTECT(t, interface);

    set(t, table, ArrayBody + (i * BytesPerWord), reinterpret_cast<object>(interface));

    hashMapInsertMaybe(t, map, reinterpret_cast<object>(name), reinterpret_cast<object>(interface), byteArrayHash, byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  object interfaceTable = 0;
  if (map->size()) {
    unsigned length = map->size();
    if ((class_->flags() & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = reinterpret_cast<object>(makeArray(t, length));
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    for (HashMapIterator it(t, map); it.hasMore();) {
      GcClass* interface = cast<GcClass>(t, it.next()->second());

      set(t, interfaceTable, ArrayBody + (i * BytesPerWord), reinterpret_cast<object>(interface));
      ++ i;

      if ((class_->flags() & ACC_INTERFACE) == 0) {
        if (interface->virtualTable()) {
          // we'll fill in this table in parseMethodTable():
          object vtable = reinterpret_cast<object>(makeArray
            (t, arrayLength(t, interface->virtualTable())));
          
          set(t, interfaceTable, ArrayBody + (i * BytesPerWord), vtable);
        }

        ++i;
      }
    }
  }

  set(t, reinterpret_cast<object>(class_), ClassInterfaceTable, interfaceTable);
}

void
parseFieldTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  unsigned memberOffset = BytesPerWord;
  if (class_->super()) {
    memberOffset = class_->super()->fixedSize();
  }

  unsigned count = s.read2();
  if (count) {
    unsigned staticOffset = BytesPerWord * 3;
    unsigned staticCount = 0;
  
    object fieldTable = reinterpret_cast<object>(makeArray(t, count));
    PROTECT(t, fieldTable);

    object staticValueTable = reinterpret_cast<object>(makeIntArray(t, count));
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
            addendum = reinterpret_cast<object>(
                makeFieldAddendum(t, pool, 0, 0));
          }
      
          set(t, addendum, AddendumSignature,
              singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("RuntimeVisibleAnnotations"),
                              &byteArrayBody(t, name, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = reinterpret_cast<object>(
                makeFieldAddendum(t, pool, 0, 0));
          }

          object body = reinterpret_cast<object>(makeByteArray(t, length));
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, AddendumAnnotationTable, body);
        } else {
          s.skip(length);
        }
      }

      GcField* field = makeField
        (t,
         0, // vm flags
         code,
         flags,
         0, // offset
         0, // native ID
         cast<GcByteArray>(t, singletonObject(t, pool, name - 1)),
         cast<GcByteArray>(t, singletonObject(t, pool, spec - 1)),
         cast<GcFieldAddendum>(t, addendum),
         class_);

      unsigned size = fieldSize(t, code);
      if (flags & ACC_STATIC) {
        staticOffset = pad(staticOffset, size);

        field->offset() = staticOffset;

        staticOffset += size;

        intArrayBody(t, staticValueTable, staticCount) = value;

        RUNTIME_ARRAY_BODY(staticTypes)[staticCount++] = code;
      } else {
        if (flags & ACC_FINAL) {
          class_->vmFlags() |= HasFinalMemberFlag;
        }

        memberOffset = pad(memberOffset, size);

        field->offset() = memberOffset;

        memberOffset += size;
      }

      set(t, fieldTable, ArrayBody + (i * BytesPerWord), reinterpret_cast<object>(field));
    }

    set(t, reinterpret_cast<object>(class_), ClassFieldTable, fieldTable);

    if (staticCount) {
      unsigned footprint = ceilingDivide(staticOffset - (BytesPerWord * 2),
                                   BytesPerWord);
      GcSingleton* staticTable = makeSingletonOfSize(t, footprint);

      uint8_t* body = reinterpret_cast<uint8_t*>
        (&singletonBody(t, reinterpret_cast<object>(staticTable), 0));

      memcpy(body, &class_, BytesPerWord);
      singletonMarkObject(t, staticTable, 0);

      for (unsigned i = 0, offset = BytesPerWord; i < staticCount; ++i) {
        unsigned size = fieldSize(t, RUNTIME_ARRAY_BODY(staticTypes)[i]);
        offset = pad(offset, size);

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

      set(t,
          reinterpret_cast<object>(class_),
          ClassStaticTable,
          reinterpret_cast<object>(staticTable));
    }
  }

  class_->fixedSize() = memberOffset;
  
  if (class_->super()
      and memberOffset == class_->super()->fixedSize())
  {
    set(t, reinterpret_cast<object>(class_), ClassObjectMask,
        reinterpret_cast<object>(class_->super()->objectMask()));
  } else {
    object mask = reinterpret_cast<object>(makeIntArray
      (t, ceilingDivide(class_->fixedSize(), 32 * BytesPerWord)));
    intArrayBody(t, mask, 0) = 1;

    object superMask = 0;
    if (class_->super()) {
      superMask = reinterpret_cast<object>(class_->super()->objectMask());
      if (superMask) {
        memcpy(&intArrayBody(t, mask, 0),
               &intArrayBody(t, superMask, 0),
               ceilingDivide(class_->super()->fixedSize(),
                       32 * BytesPerWord)
               * 4);
      }
    }

    bool sawReferenceField = false;
    object fieldTable = class_->fieldTable();
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
      set(t, reinterpret_cast<object>(class_), ClassObjectMask, mask);
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
parseCode(Thread* t, Stream& s, GcSingleton* pool)
{
  PROTECT(t, pool);

  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  if(DebugClassReader) {
    fprintf(stderr, "    code: maxStack %d maxLocals %d length %d\n", maxStack, maxLocals, length);
  }

  object code = reinterpret_cast<object>(makeCode(t, pool, 0, 0, 0, 0, 0, maxStack, maxLocals, length));
  s.read(&codeBody(t, code, 0), length);
  PROTECT(t, code);

  if(DebugClassReader) {
    disassembleCode("      ", &codeBody(t, code, 0), length);
  }

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    object eht = reinterpret_cast<object>(makeExceptionHandlerTable(t, ehtLength));
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
      object lnt = reinterpret_cast<object>(makeLineNumberTable(t, lntLength));
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
addInterfaceMethods(Thread* t, GcClass* class_, GcHashMap* virtualMap,
                    unsigned* virtualCount, bool makeList)
{
  object itable = class_->interfaceTable();
  if (itable) {
    PROTECT(t, class_);
    PROTECT(t, virtualMap);  
    PROTECT(t, itable);
    
    object list = 0;
    PROTECT(t, list);

    GcMethod* method = 0;
    PROTECT(t, method);

    object vtable = 0;
    PROTECT(t, vtable);

   unsigned stride = (class_->flags() & ACC_INTERFACE) ? 1 : 2;
   for (unsigned i = 0; i < arrayLength(t, itable); i += stride) {
      vtable = classVirtualTable(t, arrayBody(t, itable, i));
      if (vtable) {
        for (unsigned j = 0; j < arrayLength(t, vtable); ++j) {
          method = cast<GcMethod>(t, arrayBody(t, vtable, j));
          GcTriple* n = hashMapFindNode
            (t, virtualMap, reinterpret_cast<object>(method), methodHash, methodEqual);
          if (n == 0) {
            method = makeMethod
              (t,
               method->vmFlags(),
               method->returnCode(),
               method->parameterCount(),
               method->parameterFootprint(),
               method->flags(),
               (*virtualCount)++,
               0,
               0,
               method->name(),
               method->spec(),
               0,
               class_,
               0);

            hashMapInsert(t,
                          virtualMap,
                          reinterpret_cast<object>(method),
                          reinterpret_cast<object>(method),
                          methodHash);

            if (makeList) {
              if (list == 0) {
                list = reinterpret_cast<object>(vm::makeList(t, 0, 0, 0));
              }
              listAppend(t, cast<GcList>(t, list), reinterpret_cast<object>(method));
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
parseMethodTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  GcHashMap* virtualMap = makeHashMap(t, 0, 0);
  PROTECT(t, virtualMap);

  unsigned virtualCount = 0;
  unsigned declaredVirtualCount = 0;

  object superVirtualTable = 0;
  PROTECT(t, superVirtualTable);

  if ((class_->flags() & ACC_INTERFACE) == 0) {
    if (class_->super()) {
      superVirtualTable = class_->super()->virtualTable();
    }

    if (superVirtualTable) {
      virtualCount = arrayLength(t, superVirtualTable);
      for (unsigned i = 0; i < virtualCount; ++i) {
        object method = arrayBody(t, superVirtualTable, i);
        hashMapInsert(t, virtualMap, method, method, methodHash);
      }
    }
  }

  object newVirtuals = reinterpret_cast<object>(makeList(t, 0, 0, 0));
  PROTECT(t, newVirtuals);
  
  unsigned count = s.read2();

  if(DebugClassReader) {
    fprintf(stderr, "  method count %d\n", count);
  }

  if (count) {
    object methodTable = reinterpret_cast<object>(makeArray(t, count));
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
            addendum = reinterpret_cast<object>(makeMethodAddendum(t, pool, 0, 0, 0, 0, 0));
          }
          unsigned exceptionCount = s.read2();
          object body = reinterpret_cast<object>(makeShortArray(t, exceptionCount));
          for (unsigned i = 0; i < exceptionCount; ++i) {
            shortArrayBody(t, body, i) = s.read2();
          }
          set(t, addendum, MethodAddendumExceptionTable, body);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("AnnotationDefault"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = reinterpret_cast<object>(makeMethodAddendum(t, pool, 0, 0, 0, 0, 0));
          }

          object body = reinterpret_cast<object>(makeByteArray(t, length));
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, MethodAddendumAnnotationDefault, body);          
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = reinterpret_cast<object>(makeMethodAddendum(t, pool, 0, 0, 0, 0, 0));
          }
      
          set(t, addendum, AddendumSignature,
              singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("RuntimeVisibleAnnotations"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = reinterpret_cast<object>(makeMethodAddendum(t, pool, 0, 0, 0, 0, 0));
          }

          object body = reinterpret_cast<object>(makeByteArray(t, length));
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, AddendumAnnotationTable, body);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                              ("RuntimeVisibleParameterAnnotations"),
                              &byteArrayBody(t, attributeName, 0)) == 0)
        {
          if (addendum == 0) {
            addendum = reinterpret_cast<object>(makeMethodAddendum(t, pool, 0, 0, 0, 0, 0));
          }

          object body = reinterpret_cast<object>(makeByteArray(t, length));
          s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)),
                 length);

          set(t, addendum, MethodAddendumParameterAnnotationTable, body);
        } else {
          s.skip(length);
        }
      }

      const char* specString = reinterpret_cast<const char*>
        (&byteArrayBody(t, singletonObject(t, pool, spec - 1), 0));

      unsigned parameterCount;
      unsigned parameterFootprint;
      unsigned returnCode;
      scanMethodSpec(t, specString, flags & ACC_STATIC, &parameterCount,
                     &parameterFootprint, &returnCode);

      GcMethod* method =  t->m->processor->makeMethod
        (t,
         0, // vm flags
         returnCode,
         parameterCount,
         parameterFootprint,
         flags,
         0, // offset
         cast<GcByteArray>(t, singletonObject(t, pool, name - 1)),
         cast<GcByteArray>(t, singletonObject(t, pool, spec - 1)),
         cast<GcMethodAddendum>(t, addendum),
         class_,
         cast<GcCode>(t, code));

      PROTECT(t, method);

      if (methodVirtual(t, method)) {
        ++ declaredVirtualCount;

        GcTriple* p = hashMapFindNode
          (t, virtualMap, reinterpret_cast<object>(method), methodHash, methodEqual);

        if (p) {
          method->offset() = methodOffset(t, p->first());

          set(t, reinterpret_cast<object>(p), TripleSecond, reinterpret_cast<object>(method));
        } else {
          method->offset() = virtualCount++;

          listAppend(t, cast<GcList>(t, newVirtuals), reinterpret_cast<object>(method));

          hashMapInsert(t, virtualMap, reinterpret_cast<object>(method), reinterpret_cast<object>(method), methodHash);
        }

        if (UNLIKELY((class_->flags() & ACC_INTERFACE) == 0
                     and vm::strcmp
                     (reinterpret_cast<const int8_t*>("finalize"), 
                      method->name()->body().begin()) == 0
                     and vm::strcmp
                     (reinterpret_cast<const int8_t*>("()V"),
                      method->spec()->body().begin()) == 0
                     and (not emptyMethod(t, method))))
        {
          class_->vmFlags() |= HasFinalizerFlag;
        }
      } else {
        method->offset() = i;

        if (vm::strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                       method->name()->body().begin()) == 0)
        {
          method->vmFlags() |= ClassInitFlag;
          class_->vmFlags() |= NeedInitFlag;
        } else if (vm::strcmp
                   (reinterpret_cast<const int8_t*>("<init>"), 
                    method->name()->body().begin()) == 0)
        {
          method->vmFlags() |= ConstructorFlag;
        }
      }

      set(t, methodTable, ArrayBody + (i * BytesPerWord), reinterpret_cast<object>(method));
    }

    set(t, reinterpret_cast<object>(class_), ClassMethodTable, methodTable);
  }


  object abstractVirtuals = addInterfaceMethods
    (t, class_, virtualMap, &virtualCount, true);

  PROTECT(t, abstractVirtuals);

  bool populateInterfaceVtables = false;

  if (declaredVirtualCount == 0
      and abstractVirtuals == 0
      and (class_->flags() & ACC_INTERFACE) == 0)
  {
    if (class_->super()) {
      // inherit virtual table from superclass
      set(t, reinterpret_cast<object>(class_), ClassVirtualTable, superVirtualTable);
      
      if (class_->super()->interfaceTable()
          and arrayLength(t, class_->interfaceTable())
          == arrayLength
          (t, class_->super()->interfaceTable()))
      {
        // inherit interface table from superclass
        set(t, reinterpret_cast<object>(class_), ClassInterfaceTable,
            class_->super()->interfaceTable());
      } else {
        populateInterfaceVtables = true;
      }
    } else {
      // apparently, Object does not have any virtual methods.  We
      // give it a vtable anyway so code doesn't break elsewhere.
      object vtable = reinterpret_cast<object>(makeArray(t, 0));
      set(t, reinterpret_cast<object>(class_), ClassVirtualTable, vtable);
    }
  } else if (virtualCount) {
    // generate class vtable

    object vtable = reinterpret_cast<object>(makeArray(t, virtualCount));

    unsigned i = 0;
    if (class_->flags() & ACC_INTERFACE) {
      PROTECT(t, vtable);

      for (HashMapIterator it(t, virtualMap); it.hasMore();) {
        object method = it.next()->first();
        assertT(t, arrayBody(t, vtable, methodOffset(t, method)) == 0);
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
    }

    if (abstractVirtuals) {
      PROTECT(t, vtable);

      object originalMethodTable = class_->methodTable();
      PROTECT(t, originalMethodTable);

      unsigned oldLength = class_->methodTable() ?
        arrayLength(t, class_->methodTable()) : 0;

      object addendum = reinterpret_cast<object>(getClassAddendum(t, class_, pool));
      classAddendumDeclaredMethodCount(t, addendum) = oldLength;

      object newMethodTable = reinterpret_cast<object>(makeArray
        (t, oldLength + listSize(t, abstractVirtuals)));

      if (oldLength) {
        memcpy(&arrayBody(t, newMethodTable, 0),
               &arrayBody(t, class_->methodTable(), 0),
               oldLength * sizeof(object));
      }

      mark(t, newMethodTable, ArrayBody, oldLength);

      unsigned mti = oldLength;
      for (object p = listFront(t, abstractVirtuals);
           p; p = pairSecond(t, p))
      {
        set(t, newMethodTable,
            ArrayBody + ((mti++) * BytesPerWord), pairFirst(t, p));

        if ((class_->flags() & ACC_INTERFACE) == 0) {
          set(t, vtable,
              ArrayBody + ((i++) * BytesPerWord), pairFirst(t, p));
        }
      }

      assertT(t, arrayLength(t, newMethodTable) == mti);

      set(t, reinterpret_cast<object>(class_), ClassMethodTable, newMethodTable);
    }

    assertT(t, arrayLength(t, vtable) == i);

    set(t, reinterpret_cast<object>(class_), ClassVirtualTable, vtable);
  }

  if (populateInterfaceVtables) {
    // generate interface vtables
    object itable = class_->interfaceTable();
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
            assertT(t, method);
              
            set(t, vtable, ArrayBody + (j * BytesPerWord), method);
          }
        }
      }
    }
  }
}

void
parseAttributeTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool)
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
      set(t, reinterpret_cast<object>(class_), ClassSourceFile, singletonObject(t, pool, s.read2() - 1));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      set(t, reinterpret_cast<object>(addendum), AddendumSignature,
          singletonObject(t, pool, s.read2() - 1));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("InnerClasses"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned innerClassCount = s.read2();
      object table = reinterpret_cast<object>(makeArray(t, innerClassCount));
      PROTECT(t, table);

      for (unsigned i = 0; i < innerClassCount; ++i) {
        int16_t inner = s.read2();
        int16_t outer = s.read2();
        int16_t name = s.read2();
        int16_t flags = s.read2();

        object reference = reinterpret_cast<object>(makeInnerClassReference
          (t,
           inner ? referenceName(t, singletonObject(t, pool, inner - 1)) : 0,
           outer ? referenceName(t, singletonObject(t, pool, outer - 1)) : 0,
           cast<GcByteArray>(t, name ? singletonObject(t, pool, name - 1) : 0),
           flags));

        set(t, table, ArrayBody + (i * BytesPerWord), reference);
      }

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      set(t, reinterpret_cast<object>(addendum), ClassAddendumInnerClassTable, table);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                          ("RuntimeVisibleAnnotations"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      object body = reinterpret_cast<object>(makeByteArray(t, length));
      PROTECT(t, body);
      s.read(reinterpret_cast<uint8_t*>(&byteArrayBody(t, body, 0)), length);

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      set(t, reinterpret_cast<object>(addendum), AddendumAnnotationTable, body);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>
                          ("EnclosingMethod"),
                          &byteArrayBody(t, name, 0)) == 0)
    {
      int16_t enclosingClass = s.read2();
      int16_t enclosingMethod = s.read2();

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);

      set(t, addendum, ClassAddendumEnclosingClass,
          referenceName(t, singletonObject(t, pool, enclosingClass - 1)));

      set(t, reinterpret_cast<object>(addendum), ClassAddendumEnclosingMethod, enclosingMethod
          ? singletonObject(t, pool, enclosingMethod - 1) : 0);
    } else {
      s.skip(length);
    }
  }
}

void
updateClassTables(Thread* t, GcClass* newClass, GcClass* oldClass)
{
  object fieldTable = newClass->fieldTable();
  if (fieldTable) {
    for (unsigned i = 0; i < arrayLength(t, fieldTable); ++i) {
      set(t, arrayBody(t, fieldTable, i), FieldClass, reinterpret_cast<object>(newClass));
    }
  }

  object staticTable = reinterpret_cast<object>(newClass->staticTable());
  if (staticTable) {
    set(t, staticTable, SingletonBody, reinterpret_cast<object>(newClass));
  }

  if (newClass->flags() & ACC_INTERFACE) {
    object virtualTable = newClass->virtualTable();
    if (virtualTable) {
      for (unsigned i = 0; i < arrayLength(t, virtualTable); ++i) {
        if (methodClass(t, arrayBody(t, virtualTable, i)) == reinterpret_cast<object>(oldClass)) {
          set(t, arrayBody(t, virtualTable, i), MethodClass, reinterpret_cast<object>(newClass));
        }
      }
    }
  }

  object methodTable = newClass->methodTable();
  if (methodTable) {
    for (unsigned i = 0; i < arrayLength(t, methodTable); ++i) {
      set(t, arrayBody(t, methodTable, i), MethodClass, reinterpret_cast<object>(newClass));
    }
  }
}

void
updateBootstrapClass(Thread* t, GcClass* bootstrapClass, GcClass* class_)
{
  expect(t, bootstrapClass != class_);

  // verify that the classes have the same layout
  expect(t, bootstrapClass->super() == class_->super());

  expect(t, bootstrapClass->fixedSize() >= class_->fixedSize());

  expect(t, (class_->vmFlags() & HasFinalizerFlag) == 0);

  PROTECT(t, bootstrapClass);
  PROTECT(t, class_);

  ENTER(t, Thread::ExclusiveState);

  bootstrapClass->vmFlags() &= ~BootstrapFlag;
  bootstrapClass->vmFlags() |= class_->vmFlags();
  bootstrapClass->flags() |= class_->flags();

  set(t, reinterpret_cast<object>(bootstrapClass), ClassArrayElementClass, reinterpret_cast<object>(class_->arrayElementClass()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassSuper, reinterpret_cast<object>(class_->super()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassInterfaceTable, reinterpret_cast<object>(class_->interfaceTable()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassVirtualTable, reinterpret_cast<object>(class_->virtualTable()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassFieldTable, reinterpret_cast<object>(class_->fieldTable()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassMethodTable, reinterpret_cast<object>(class_->methodTable()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassStaticTable, reinterpret_cast<object>(class_->staticTable()));
  set(t, reinterpret_cast<object>(bootstrapClass), ClassAddendum, reinterpret_cast<object>(class_->addendum()));

  updateClassTables(t, bootstrapClass, class_);
}

GcClass*
makeArrayClass(Thread* t, GcClassLoader* loader, unsigned dimensions, GcByteArray* spec,
               GcClass* elementClass)
{
  if (type(t, GcJobject::Type)->vmFlags() & BootstrapFlag) {
    PROTECT(t, loader);
    PROTECT(t, spec);
    PROTECT(t, elementClass);

    // Load java.lang.Object if present so we can use its vtable, but
    // don't throw an exception if we can't find it.  This way, we
    // avoid infinite recursion due to trying to create an array to
    // make a stack trace for a ClassNotFoundException.
    resolveSystemClass
      (t, cast<GcClassLoader>(t, root(t, Machine::BootLoader)),
       type(t, GcJobject::Type)->name(), false);
  }

  object vtable = type(t, GcJobject::Type)->virtualTable();

  GcClass* c = t->m->processor->makeClass
    (t,
     0,
     0,
     2 * BytesPerWord,
     BytesPerWord,
     dimensions,
     elementClass,
     type(t, GcArray::Type)->objectMask(),
     spec,
     0,
     type(t, GcJobject::Type),
     root(t, Machine::ArrayInterfaceTable),
     vtable,
     0,
     0,
     0,
     0,
     loader,
     arrayLength(t, vtable));

  PROTECT(t, c);

  t->m->processor->initVtable(t, c);

  return c;
}

void
saveLoadedClass(Thread* t, GcClassLoader* loader, GcClass* c)
{
  PROTECT(t, loader);
  PROTECT(t, c);

  ACQUIRE(t, t->m->classLock);

  if (loader->map() == 0) {
    GcHashMap* map = makeHashMap(t, 0, 0);
    set(t, reinterpret_cast<object>(loader), ClassLoaderMap, reinterpret_cast<object>(map));
  }

  hashMapInsert(t,
                cast<GcHashMap>(t, loader->map()),
                reinterpret_cast<object>(c->name()),
                reinterpret_cast<object>(c),
                byteArrayHash);
}

GcClass*
makeArrayClass(Thread* t, GcClassLoader* loader, GcByteArray* spec, bool throw_,
               Gc::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  const char* s = reinterpret_cast<const char*>(spec->body().begin());
  const char* start = s;
  unsigned dimensions = 0;
  for (; *s == '['; ++s) ++ dimensions;

  GcByteArray* elementSpec;
  switch (*s) {
  case 'L': {
    ++ s;
    const char* elementSpecStart = s;
    while (*s and *s != ';') ++ s;
    if (dimensions > 1) {
      elementSpecStart -= dimensions;
      ++ s;
    }

    elementSpec = makeByteArray(t, s - elementSpecStart + 1);
    memcpy(elementSpec->body().begin(),
           &spec->body()[elementSpecStart - start],
           s - elementSpecStart);
    elementSpec->body()[s - elementSpecStart] = 0;
  } break;

  default:
    if (dimensions > 1) {
      char c = *s;
      elementSpec = makeByteArray(t, dimensions + 1);
      unsigned i;
      for (i = 0; i < dimensions - 1; ++i) {
        elementSpec->body()[i] = '[';
      }
      elementSpec->body()[i++] = c;
      elementSpec->body()[i] = 0;
      -- dimensions;
    } else {
      abort(t);
    }
  }

  GcClass* elementClass = cast<GcClass>(t, hashMapFind
    (t, cast<GcHashMap>(t, root(t, Machine::BootstrapClassMap)), reinterpret_cast<object>(elementSpec), byteArrayHash,
     byteArrayEqual));
  
  if (elementClass == 0) {
    elementClass = resolveClass(t, loader, elementSpec, throw_, throwType);
    if (elementClass == 0) return 0;
  }

  PROTECT(t, elementClass);

  ACQUIRE(t, t->m->classLock);

  GcClass* class_ = findLoadedClass(t, elementClass->loader(), spec);
  if (class_) {
    return class_;
  }

  class_ = makeArrayClass
    (t, elementClass->loader(), dimensions, spec, elementClass);

  PROTECT(t, class_);

  saveLoadedClass(t, elementClass->loader(), class_);

  return class_;
}

GcClass*
resolveArrayClass(Thread* t, GcClassLoader* loader, GcByteArray* spec, bool throw_,
                  Gc::Type throwType)
{
  GcClass* c = cast<GcClass>(t,
                             hashMapFind(t,
                                         cast<GcHashMap>(t, root(t, Machine::BootstrapClassMap)),
                                         reinterpret_cast<object>(spec),
                                         byteArrayHash,
                                         byteArrayEqual));

  if (c) {
    set(t, reinterpret_cast<object>(c), ClassVirtualTable,
        type(t, GcJobject::Type)->virtualTable());

    return c;
  } else {
    PROTECT(t, loader);
    PROTECT(t, spec);

    c = findLoadedClass(t, cast<GcClassLoader>(t, root(t, Machine::BootLoader)), spec);

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
    (t, cast<GcHashMap>(t, root(t, Machine::MonitorMap)), o, objectHash, objectEqual);

  if (DebugMonitors) {
    fprintf(stderr, "dispose monitor %p for object %x\n", m, hash);
  }
}

void
removeString(Thread* t, object o)
{
  hashMapRemove(t, cast<GcHashMap>(t, root(t, Machine::StringMap)), o, stringHash, objectEqual);
}

void
bootClass(Thread* t, Gc::Type type, int superType, uint32_t objectMask,
          unsigned fixedSize, unsigned arrayElementSize, unsigned vtableLength)
{
  GcClass* super = (superType >= 0
                  ? vm::type(t, static_cast<Gc::Type>(superType)) : 0);

  object mask;
  if (objectMask) {
    if (super
        and super->objectMask()
        and super->objectMask()->body()[0]
        == static_cast<int32_t>(objectMask))
    {
      mask = reinterpret_cast<object>(vm::type(t, static_cast<Gc::Type>(superType))->objectMask());
    } else {
      mask = reinterpret_cast<object>(makeIntArray(t, 1));
      intArrayBody(t, mask, 0) = objectMask;
    }
  } else {
    mask = 0;
  }

  super = (superType >= 0
           ? vm::type(t, static_cast<Gc::Type>(superType)) : 0);

  GcClass* class_ = t->m->processor->makeClass
    (t, 0, BootstrapFlag, fixedSize, arrayElementSize,
     arrayElementSize ? 1 : 0, 0, cast<GcIntArray>(t, mask), 0, 0, super, 0, 0, 0, 0, 0, 0,
     cast<GcClassLoader>(t, root(t, Machine::BootLoader)), vtableLength);

  setType(t, type, class_);
}

void
bootJavaClass(Thread* t, Gc::Type type, int superType, const char* name,
              int vtableLength, object bootMethod)
{
  PROTECT(t, bootMethod);

  object n = reinterpret_cast<object>(makeByteArray(t, name));
  PROTECT(t, n);

  GcClass* class_ = vm::type(t, type);
  PROTECT(t, class_);

  set(t, reinterpret_cast<object>(class_), ClassName, n);

  object vtable;
  if (vtableLength >= 0) {
    vtable = reinterpret_cast<object>(makeArray(t, vtableLength));
    for (int i = 0; i < vtableLength; ++ i) {
      arrayBody(t, vtable, i) = bootMethod;
    }
  } else {
    vtable = vm::type(t, static_cast<Gc::Type>(superType))->virtualTable();
  }

  set(t, reinterpret_cast<object>(class_), ClassVirtualTable, vtable);

  t->m->processor->initVtable(t, class_);

  hashMapInsert
    (t, cast<GcHashMap>(t, root(t, Machine::BootstrapClassMap)), n, reinterpret_cast<object>(class_), byteArrayHash);
}

void
nameClass(Thread* t, Gc::Type type, const char* name)
{
  object n = reinterpret_cast<object>(makeByteArray(t, name));
  set(t, t->m->types->body()[type], ClassName, n);
}

void
makeArrayInterfaceTable(Thread* t)
{
  object interfaceTable = reinterpret_cast<object>(makeArray(t, 4));
  
  set(t, interfaceTable, ArrayBody, reinterpret_cast<object>(type
      (t, GcSerializable::Type)));
  
  set(t, interfaceTable, ArrayBody + (2 * BytesPerWord),
      reinterpret_cast<object>(type(t, GcCloneable::Type)));
  
  setRoot(t, Machine::ArrayInterfaceTable, interfaceTable);
}

void
boot(Thread* t)
{
  Machine* m = t->m;

  m->unsafe = true;

  m->roots = reinterpret_cast<GcArray*>(allocate(t, pad((Machine::RootCount + 2) * BytesPerWord), true));
  m->roots->length() = Machine::RootCount;

  setRoot(t, Machine::BootLoader,
          allocate(t, GcSystemClassLoader::FixedSize, true));

  setRoot(t, Machine::AppLoader,
          allocate(t, GcSystemClassLoader::FixedSize, true));

  m->types = reinterpret_cast<GcArray*>(allocate(t, pad((TypeCount + 2) * BytesPerWord), true));
  m->types->length() = TypeCount;

#include "type-initializations.cpp"

  GcClass* arrayClass = type(t, GcArray::Type);
  set(t, m->types, 0, arrayClass);
  set(t, m->roots, 0, arrayClass);

  GcClass* loaderClass = type(t, GcSystemClassLoader::Type);
  set(t, root(t, Machine::BootLoader), 0, reinterpret_cast<object>(loaderClass));
  set(t, root(t, Machine::AppLoader), 0, reinterpret_cast<object>(loaderClass));

  GcClass* objectClass = type(t, GcJobject::Type);

  GcClass* classClass = type(t, GcClass::Type);
  set(t, reinterpret_cast<object>(classClass), 0, reinterpret_cast<object>(classClass));
  set(t, reinterpret_cast<object>(classClass), ClassSuper, reinterpret_cast<object>(objectClass));

  GcClass* intArrayClass = type(t, GcIntArray::Type);
  set(t, reinterpret_cast<object>(intArrayClass), 0, reinterpret_cast<object>(classClass));
  set(t, reinterpret_cast<object>(intArrayClass), ClassSuper, reinterpret_cast<object>(objectClass));

  m->unsafe = false;

  type(t, GcSingleton::Type)->vmFlags()
    |= SingletonFlag;

  type(t, GcContinuation::Type)->vmFlags()
    |= ContinuationFlag;

  type(t, GcJreference::Type)->vmFlags()
    |= ReferenceFlag;
  type(t, GcWeakReference::Type)->vmFlags()
    |= ReferenceFlag | WeakReferenceFlag;
  type(t, GcSoftReference::Type)->vmFlags()
    |= ReferenceFlag | WeakReferenceFlag;
  type(t, GcPhantomReference::Type)->vmFlags()
    |= ReferenceFlag | WeakReferenceFlag;

  type(t, GcJboolean::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJbyte::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJchar::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJshort::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJint::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJlong::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJfloat::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJdouble::Type)->vmFlags()
    |= PrimitiveFlag;
  type(t, GcJvoid::Type)->vmFlags()
    |= PrimitiveFlag;

  set(t, reinterpret_cast<object>(type(t, GcBooleanArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJboolean::Type)));
  set(t, reinterpret_cast<object>(type(t, GcByteArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJbyte::Type)));
  set(t, reinterpret_cast<object>(type(t, GcCharArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJchar::Type)));
  set(t, reinterpret_cast<object>(type(t, GcShortArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJshort::Type)));
  set(t, reinterpret_cast<object>(type(t, GcIntArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJint::Type)));
  set(t, reinterpret_cast<object>(type(t, GcLongArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJlong::Type)));
  set(t, reinterpret_cast<object>(type(t, GcFloatArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJfloat::Type)));
  set(t, reinterpret_cast<object>(type(t, GcDoubleArray::Type)), ClassArrayElementClass,
      reinterpret_cast<object>(type(t, GcJdouble::Type)));

  { GcHashMap* map = makeHashMap(t, 0, 0);
    set(t, root(t, Machine::BootLoader), ClassLoaderMap, reinterpret_cast<object>(map));
  }

  cast<GcSystemClassLoader>(t, root(t, Machine::BootLoader))->finder() = m->bootFinder;

  { GcHashMap* map = makeHashMap(t, 0, 0);
    set(t, root(t, Machine::AppLoader), ClassLoaderMap, reinterpret_cast<object>(map));
  }

  cast<GcSystemClassLoader>(t, root(t, Machine::AppLoader))->finder() = m->appFinder;

  set(t, root(t, Machine::AppLoader), ClassLoaderParent,
      root(t, Machine::BootLoader));

  setRoot(t, Machine::BootstrapClassMap, reinterpret_cast<object>(makeHashMap(t, 0, 0)));

  setRoot(t, Machine::StringMap, reinterpret_cast<object>(makeWeakHashMap(t, 0, 0)));

  makeArrayInterfaceTable(t);

  set(t, reinterpret_cast<object>(type(t, GcBooleanArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcByteArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcCharArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcShortArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcIntArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcLongArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcFloatArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));
  set(t, reinterpret_cast<object>(type(t, GcDoubleArray::Type)), ClassInterfaceTable,
      root(t, Machine::ArrayInterfaceTable));

  m->processor->boot(t, 0, 0);

  { object bootCode = reinterpret_cast<object>(makeCode(t, 0, 0, 0, 0, 0, 0, 0, 0, 1));
    codeBody(t, bootCode, 0) = impdep1;
    object bootMethod = reinterpret_cast<object>(makeMethod
      (t, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, cast<GcCode>(t, bootCode)));
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

    unsigned n = baseSize(t, o, cast<GcClass>(t, static_cast<object>
                          (m->heap->follow(objectClass(t, o)))));

    if (objectExtended(t, o)) {
      ++ n;
    }

    return n;
  }

  virtual unsigned copiedSizeInWords(void* p) {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));
    assertT(t, not objectFixed(t, o));

    unsigned n = baseSize(t, o, cast<GcClass>(t, static_cast<object>
                          (m->heap->follow(objectClass(t, o)))));

    if (objectExtended(t, o) or hashTaken(t, o)) {
      ++ n;
    }

    return n;
  }

  virtual void copy(void* srcp, void* dstp) {
    Thread* t = m->rootThread;

    object src = static_cast<object>(m->heap->follow(maskAlignedPointer(srcp)));
    assertT(t, not objectFixed(t, src));

    GcClass* class_ = cast<GcClass>(t, static_cast<object>
      (m->heap->follow(objectClass(t, src))));

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

  object finalizeQueue = reinterpret_cast<object>(t->m->finalizeQueue);
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
      (m, cast<GcThread>(t, root(t, Machine::FinalizerThread)), m->rootThread);
    
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
  GcMethod* method = cast<GcMethod>(t, reinterpret_cast<object>(arguments[0]));
  object loader = reinterpret_cast<object>(arguments[1]);
  object specString = reinterpret_cast<object>(arguments[2]);

  return reinterpret_cast<uintptr_t>
    (t->m->processor->invoke(t, method, loader, specString));
}

bool
isInitializing(Thread* t, GcClass* c)
{
  for (Thread::ClassInitStack* s = t->classInitStack; s; s = s->next) {
    if (s->class_ == c) {
      return true;
    }
  }
  return false;
}

object
findInTable(Thread* t, object table, GcByteArray* name, GcByteArray* spec,
            object& (*getName)(Thread*, object),
            object& (*getSpec)(Thread*, object))
{
  if (table) {
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object o = arrayBody(t, table, i);      
      if (vm::strcmp(&byteArrayBody(t, getName(t, o), 0),
                    name->body().begin()) == 0 and
          vm::strcmp(&byteArrayBody(t, getSpec(t, o), 0),
                     spec->body().begin()) == 0)
      {
        return o;
      }
    }

//     fprintf(stderr, "%s %s not in\n",
//             &byteArrayBody(t, name, 0),
//             spec->body().begin());

//     for (unsigned i = 0; i < arrayLength(t, table); ++i) {
//       object o = arrayBody(t, table, i); 
//       fprintf(stderr, "\t%s %s\n",
//               &byteArrayBody(t, getName(t, o), 0),
//               &byteArrayBody(t, getSpec(t, o), 0)); 
//     }
  }

  return 0;
}

void
updatePackageMap(Thread* t, GcClass* class_)
{
  PROTECT(t, class_);

  if (root(t, Machine::PackageMap) == 0) {
    setRoot(t, Machine::PackageMap, reinterpret_cast<object>(makeHashMap(t, 0, 0)));
  }

  object className = reinterpret_cast<object>(class_->name());
  if ('[' != byteArrayBody(t, className, 0)) {
    THREAD_RUNTIME_ARRAY
      (t, char, packageName, byteArrayLength(t, className));

    char* s = reinterpret_cast<char*>(&byteArrayBody(t, className, 0));
    char* p = strrchr(s, '/');

    if (p) {
      int length = (p - s) + 1;
      memcpy(RUNTIME_ARRAY_BODY(packageName),
             &byteArrayBody(t, className, 0),
             length);
      RUNTIME_ARRAY_BODY(packageName)[length] = 0;

      object key = reinterpret_cast<object>(vm::makeByteArray
        (t, "%s", RUNTIME_ARRAY_BODY(packageName)));
      PROTECT(t, key);

      hashMapRemove
        (t, cast<GcHashMap>(t, root(t, Machine::PackageMap)), key, byteArrayHash,
         byteArrayEqual);

      object source = reinterpret_cast<object>(class_->source());
      if (source) {
        // note that we strip the "file:" prefix, since OpenJDK's
        // Package.defineSystemPackage expects an unadorned filename:
        const unsigned PrefixLength = 5;
        unsigned sourceNameLength = byteArrayLength(t, source)
          - PrefixLength;
        THREAD_RUNTIME_ARRAY(t, char, sourceName, sourceNameLength);
        memcpy(RUNTIME_ARRAY_BODY(sourceName),
               &byteArrayBody(t, source, PrefixLength),
               sourceNameLength);

        source = reinterpret_cast<object>(vm::makeByteArray(t, "%s", RUNTIME_ARRAY_BODY(sourceName)));
      } else {
        source = reinterpret_cast<object>(vm::makeByteArray(t, "avian-dummy-package-source"));
      }

      hashMapInsert
        (t, cast<GcHashMap>(t, root(t, Machine::PackageMap)), key, source, byteArrayHash);
    }
  }
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

  // Copying the properties memory (to avoid memory crashes)
  this->properties = (char**)heap->allocate(sizeof(char*) * propertyCount);
  for (unsigned int i = 0; i < propertyCount; i++)
  {
    size_t length = strlen(properties[i]) + 1; // +1 for null-terminating char
    this->properties[i] = (char*)heap->allocate(sizeof(char) * length);
    memcpy(this->properties[i], properties[i], length);
  }

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

  for (unsigned int i = 0; i < propertyCount; i++)
  {
    heap->free(properties[i], sizeof(char) * (strlen(properties[i]) + 1));
  }
  heap->free(properties, sizeof(const char*) * propertyCount);

  static_cast<HeapClient*>(heapClient)->dispose();

  heap->free(this, sizeof(*this));
}

Thread::Thread(Machine* m, GcThread* javaThread, Thread* parent):
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
  libraryLoadStack(0),
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
    assertT(this, m->rootThread == 0);
    assertT(this, javaThread == 0);

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

    setRoot(this, Machine::ByteArrayMap, reinterpret_cast<object>(makeWeakHashMap(this, 0, 0)));
    setRoot(this, Machine::MonitorMap, reinterpret_cast<object>(makeWeakHashMap(this, 0, 0)));

    setRoot(this, Machine::ClassRuntimeDataTable, reinterpret_cast<object>(makeVector(this, 0, 0)));
    setRoot(this, Machine::MethodRuntimeDataTable, reinterpret_cast<object>(makeVector(this, 0, 0)));
    setRoot(this, Machine::JNIMethodTable, reinterpret_cast<object>(makeVector(this, 0, 0)));
    setRoot(this, Machine::JNIFieldTable, reinterpret_cast<object>(makeVector(this, 0, 0)));

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
      javaThread->peer() = 0;

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
    startThread(t, cast<GcThread>(t, pairFirst(t, h)));
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
      assertT(t, t->m->activeCount > 0);
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
      assertT(t, t->m->exclusive == t);
      t->m->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assertT(t, t->m->activeCount > 0);
    INCREMENT(&(t->m->activeCount), -1);

    if (s == Thread::ZombieState) {
      assertT(t, t->m->liveCount > 0);
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
        assertT(t, t->m->exclusive == t);

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
      assertT(t, t->m->exclusive == t);
      // exit state should also be exclusive, so don't set exclusive = 0

      t->m->stateLock->notifyAll(t->systemThread);
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assertT(t, t->m->activeCount > 0);
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
      throw_(t, cast<GcThrowable>(t, root(t, Machine::OutOfMemoryError)));
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
makeNewGeneral(Thread* t, GcClass* class_)
{
  assertT(t, t->state == Thread::ActiveState);

  PROTECT(t, class_);

  object instance = makeNew(t, class_);
  PROTECT(t, instance);

  if (class_->vmFlags() & WeakReferenceFlag) {
    ACQUIRE(t, t->m->referenceLock);
    
    jreferenceVmNext(t, instance) = reinterpret_cast<object>(t->m->weakReferences);
    t->m->weakReferences = cast<GcJreference>(t, instance);
  }

  if (class_->vmFlags() & HasFinalizerFlag) {
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

GcByteArray*
makeByteArrayV(Thread* t, const char* format, va_list a, int size)
{
  THREAD_RUNTIME_ARRAY(t, char, buffer, size);
  
  int r = vm::vsnprintf(RUNTIME_ARRAY_BODY(buffer), size - 1, format, a);
  if (r >= 0 and r < size - 1) {
    GcByteArray* s = makeByteArray(t, strlen(RUNTIME_ARRAY_BODY(buffer)) + 1);
    memcpy(s->body().begin(), RUNTIME_ARRAY_BODY(buffer),
           s->length());
    return s;
  } else {
    return 0;
  }
}

GcByteArray*
makeByteArray(Thread* t, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    GcByteArray* s = makeByteArrayV(t, format, a, size);
    va_end(a);

    if (s) {
      return s;
    } else {
      size *= 2;
    }
  }
}

GcString*
makeString(Thread* t, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    GcByteArray* s = makeByteArrayV(t, format, a, size);
    va_end(a);

    if (s) {
      return t->m->classpath->makeString(t, reinterpret_cast<object>(s), 0, s->length() - 1);
    } else {
      size *= 2;
    }
  }
}

int
stringUTFLength(Thread* t, GcString* string, unsigned start, unsigned length)
{
  unsigned result = 0;

  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      result = length;
    } else {
      for (unsigned i = 0; i < length; ++i) {
        uint16_t c = charArrayBody
          (t, data, string->offset(t) + start + i);
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
stringChars(Thread* t, GcString* string, unsigned start, unsigned length,
            char* chars)
{
  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      memcpy(chars,
             &byteArrayBody(t, data, string->offset(t) + start),
             length);
    } else {
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = charArrayBody(t, data, string->offset(t) + start + i);
      }
    }
  }
  chars[length] = 0;
}

void
stringChars(Thread* t, GcString* string, unsigned start, unsigned length,
            uint16_t* chars)
{
  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = byteArrayBody(t, data, string->offset(t) + start + i);
      }
    } else {
      memcpy(chars,
             &charArrayBody(t, data, string->offset(t) + start),
             length * sizeof(uint16_t));
    }
  }
  chars[length] = 0;
}

void
stringUTFChars(Thread* t, GcString* string, unsigned start, unsigned length,
               char* chars, unsigned charsLength UNUSED)
{
  assertT(t, static_cast<unsigned>
         (stringUTFLength(t, string, start, length)) == charsLength);

  object data = string->data();
  if (objectClass(t, data) == type(t, GcByteArray::Type)) {    
    memcpy(chars,
           &byteArrayBody(t, data, string->offset(t) + start),
           length);
    chars[length] = 0; 
  } else {
    int j = 0;
    for (unsigned i = 0; i < length; ++i) {
      uint16_t c = charArrayBody
        (t, data, string->offset(t) + start + i);
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
  GcByteArray* name = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[0]));

  resolveSystemClass(t, cast<GcClassLoader>(t, root(t, Machine::BootLoader)), name);

  return 1;
}

bool
isAssignableFrom(Thread* t, GcClass* a, GcClass* b)
{
  assertT(t, a);
  assertT(t, b);

  if (a == b) return true;

  if (a->flags() & ACC_INTERFACE) {
    if (b->vmFlags() & BootstrapFlag) {
      uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(b->name()) };

      if (run(t, resolveBootstrap, arguments) == 0) {
        t->exception = 0;
        return false;
      }
    }

    object itable = b->interfaceTable();
    if (itable) {
      unsigned stride = (b->flags() & ACC_INTERFACE) ? 1 : 2;
      for (unsigned i = 0; i < arrayLength(t, itable); i += stride) {
        if (arrayBody(t, itable, i) == reinterpret_cast<object>(a)) {
          return true;
        }
      }
    }
  } else if (a->arrayDimensions()) {
    if (b->arrayDimensions()) {
      return isAssignableFrom
        (t, a->arrayElementClass(), b->arrayElementClass());
    }
  } else if ((a->vmFlags() & PrimitiveFlag)
             == (b->vmFlags() & PrimitiveFlag))
  {
    for (; b; b = b->super()) {
      if (b == a) {
        return true;
      }
    }
  }

  return false;
}

bool
instanceOf(Thread* t, GcClass* class_, object o)
{
  if (o == 0) {
    return false;
  } else {
    return isAssignableFrom(t, class_, objectClass(t, o));
  }
}

GcMethod*
classInitializer(Thread* t, GcClass* class_)
{
  if (class_->methodTable()) {
    for (unsigned i = 0; i < arrayLength(t, class_->methodTable()); ++i)
    {
      GcMethod* o = cast<GcMethod>(t, arrayBody(t, class_->methodTable(), i));

      if (o->vmFlags() & ClassInitFlag) {
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

GcClass*
parseClass(Thread* t, GcClassLoader* loader, const uint8_t* data, unsigned size,
           Gc::Type throwType)
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

  GcSingleton* pool = parsePool(t, s);
  PROTECT(t, pool);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  GcClass* class_ = (GcClass*)makeClass(t,
                            flags,
                            0, // VM flags
                            0, // fixed size
                            0, // array size
                            0, // array dimensions
                            0, // array element class
                            0, // runtime data index
                            0, // object mask
                            referenceName(t, singletonObject(t, pool, name - 1)),
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
    GcClass* sc = resolveClass
      (t, loader, referenceName(t, singletonObject(t, pool, super - 1)),
       true, throwType);

    set(t, reinterpret_cast<object>(class_), ClassSuper, reinterpret_cast<object>(sc));

    class_->vmFlags()
      |= (sc->vmFlags()
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

  object vtable = class_->virtualTable();
  unsigned vtableLength = (vtable ? arrayLength(t, vtable) : 0);

  GcClass* real = t->m->processor->makeClass
    (t,
     class_->flags(),
     class_->vmFlags(),
     class_->fixedSize(),
     class_->arrayElementSize(),
     class_->arrayDimensions(),
     class_->arrayElementClass(),
     class_->objectMask(),
     class_->name(),
     class_->sourceFile(),
     class_->super(),
     class_->interfaceTable(),
     class_->virtualTable(),
     class_->fieldTable(),
     class_->methodTable(),
     class_->addendum(),
     class_->staticTable(),
     class_->loader(),
     vtableLength);

  PROTECT(t, real);

  t->m->processor->initVtable(t, real);

  updateClassTables(t, real, class_);

  if (root(t, Machine::PoolMap)) {
    object bootstrapClass = hashMapFind
      (t, cast<GcHashMap>(t, root(t, Machine::BootstrapClassMap)), reinterpret_cast<object>(class_->name()),
       byteArrayHash, byteArrayEqual);

    hashMapInsert(
        t,
        cast<GcHashMap>(t, root(t, Machine::PoolMap)),
        bootstrapClass ? bootstrapClass : reinterpret_cast<object>(real),
        reinterpret_cast<object>(pool),
        objectHash);
  }

  return real;
}

uint64_t
runParseClass(Thread* t, uintptr_t* arguments)
{
  GcClassLoader* loader = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  System::Region* region = reinterpret_cast<System::Region*>(arguments[1]);
  Gc::Type throwType = static_cast<Gc::Type>(arguments[2]);

  return reinterpret_cast<uintptr_t>
    (parseClass(t, loader, region->start(), region->length(), throwType));
}

GcClass*
resolveSystemClass(Thread* t, GcClassLoader* loader, GcByteArray* spec, bool throw_,
                   Gc::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  GcClass* class_ = cast<GcClass>(t, hashMapFind
    (t, cast<GcHashMap>(t, loader->map()), reinterpret_cast<object>(spec), byteArrayHash, byteArrayEqual));

  if (class_ == 0) {
    PROTECT(t, class_);

    if (loader->parent()) {
      class_ = resolveSystemClass
        (t, loader->parent(), spec, false);
      if (class_) {
        return class_;
      }
    }

    if (spec->body()[0] == '[') {
      class_ = resolveArrayClass(t, loader, spec, throw_, throwType);
    } else {
      THREAD_RUNTIME_ARRAY(t, char, file, spec->length() + 6);
      memcpy(RUNTIME_ARRAY_BODY(file),
             spec->body().begin(),
             spec->length() - 1);
      memcpy(RUNTIME_ARRAY_BODY(file) + spec->length() - 1,
             ".class",
             7);

      System::Region* region = static_cast<Finder*>
        (loader->as<GcSystemClassLoader>(t)->finder())->find
        (RUNTIME_ARRAY_BODY(file));

      if (region) {
        if (Verbose) {
          fprintf(stderr, "parsing %s\n", spec->body().begin());
        }

        { THREAD_RESOURCE(t, System::Region*, region, region->dispose());

          uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(loader),
                                    reinterpret_cast<uintptr_t>(region),
                                    static_cast<uintptr_t>(throwType) };

          // parse class file
          class_ = cast<GcClass>
            (t, reinterpret_cast<object>(runRaw(t, runParseClass, arguments)));

          if (UNLIKELY(t->exception)) {
            if (throw_) {
              GcThrowable* e = t->exception;
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
                  spec->body().begin(),
                  class_);
        }

        { const char* source = static_cast<Finder*>
            (loader->as<GcSystemClassLoader>(t)->finder())->sourceUrl
            (RUNTIME_ARRAY_BODY(file));
          
          if (source) {
            unsigned length = strlen(source);
            object array = reinterpret_cast<object>(makeByteArray(t, length + 1));
            memcpy(&byteArrayBody(t, array, 0), source, length);
            array = internByteArray(t, array);
            
            set(t, reinterpret_cast<object>(class_), ClassSource, array);
          }
        }

        GcClass* bootstrapClass = cast<GcClass>(t, hashMapFind
          (t, cast<GcHashMap>(t, root(t, Machine::BootstrapClassMap)), reinterpret_cast<object>(spec), byteArrayHash,
           byteArrayEqual));

        if (bootstrapClass) {
          PROTECT(t, bootstrapClass);
          
          updateBootstrapClass(t, bootstrapClass, class_);
          class_ = bootstrapClass;
        }
      }
    }

    if (class_) {
      hashMapInsert(t, cast<GcHashMap>(t, loader->map()), reinterpret_cast<object>(spec), reinterpret_cast<object>(class_), byteArrayHash);

      updatePackageMap(t, class_);
    } else if (throw_) {
      throwNew(t, throwType, "%s", spec->body().begin());
    }
  }

  return class_;
}

GcClass*
findLoadedClass(Thread* t, GcClassLoader* loader, GcByteArray* spec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  return loader->map() ? cast<GcClass>(t, hashMapFind
    (t, cast<GcHashMap>(t, loader->map()), reinterpret_cast<object>(spec), byteArrayHash, byteArrayEqual)) : 0;
}

GcClass*
resolveClass(Thread* t, GcClassLoader* loader, GcByteArray* spec, bool throw_,
             Gc::Type throwType)
{
  if (objectClass(t, loader) == type(t, GcSystemClassLoader::Type)) {
    return resolveSystemClass(t, loader, spec, throw_, throwType);
  } else {
    PROTECT(t, loader);
    PROTECT(t, spec);

    GcClass* c = findLoadedClass(t, loader, spec);
    if (c) {
      return c;
    }

    if (spec->body()[0] == '[') {
      c = resolveArrayClass(t, loader, spec, throw_, throwType);
    } else {
      if (root(t, Machine::LoadClassMethod) == 0) {
        GcMethod* m = resolveMethod
          (t, cast<GcClassLoader>(t, root(t, Machine::BootLoader)), "java/lang/ClassLoader",
           "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

        if (m) {
          setRoot(t, Machine::LoadClassMethod, reinterpret_cast<object>(m));

          GcClass* classLoaderClass = type(t, GcClassLoader::Type);
        
          if (classLoaderClass->vmFlags() & BootstrapFlag) {
            resolveSystemClass
              (t, cast<GcClassLoader>(t, root(t, Machine::BootLoader)),
               classLoaderClass->name());
          }
        }      
      }

      GcMethod* method = findVirtualMethod
        (t, cast<GcMethod>(t, root(t, Machine::LoadClassMethod)), objectClass(t, loader));

      PROTECT(t, method);
        
      THREAD_RUNTIME_ARRAY(t, char, s, spec->length());
      replace('/', '.', RUNTIME_ARRAY_BODY(s), reinterpret_cast<char*>
              (spec->body().begin()));

      GcString* specString = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));
      PROTECT(t, specString);

      uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(method),
                                reinterpret_cast<uintptr_t>(loader),
                                reinterpret_cast<uintptr_t>(specString) };

      object jc = reinterpret_cast<object>
        (runRaw(t, invokeLoadClass, arguments));

      if (LIKELY(jc)) {
        c = reinterpret_cast<GcClass*>(jclassVmClass(t, jc));
      } else if (t->exception) {
        if (throw_) {
          GcThrowable* e = type(t, throwType) == objectClass(t, t->exception)
            ? t->exception
            : makeThrowable(t, throwType, reinterpret_cast<object>(specString), 0, t->exception);
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
      throwNew(t, throwType, "%s", spec->body().begin());
    }

    return c;
  }
}

GcMethod*
resolveMethod(Thread* t, GcClass* class_, const char* methodName,
              const char* methodSpec)
{
  PROTECT(t, class_);

  GcByteArray* name = makeByteArray(t, methodName);
  PROTECT(t, name);

  GcByteArray* spec = makeByteArray(t, methodSpec);
    
  GcMethod* method = cast<GcMethod>(t, findMethodInClass(t, class_, name, spec));

  if (method == 0) {
    throwNew(t, GcNoSuchMethodError::Type, "%s %s not found in %s",
             methodName, methodSpec, class_->name()->body().begin());
  } else {
    return method;
  }
}

GcField*
resolveField(Thread* t, GcClass* class_, const char* fieldName,
              const char* fieldSpec)
{
  PROTECT(t, class_);

  GcByteArray* name = makeByteArray(t, fieldName);
  PROTECT(t, name);

  GcByteArray* spec = makeByteArray(t, fieldSpec);
  PROTECT(t, spec);
 
  object field = findInInterfaces(t, class_, name, spec, findFieldInClass);

  GcClass* c = class_;
  PROTECT(t, c);

  for (; c != 0 and field == 0; c = c->super()) {
    field = findFieldInClass(t, c, name, spec);
  }

  if (field == 0) {
    throwNew(t, GcNoSuchFieldError::Type, "%s %s not found in %s",
             fieldName, fieldSpec, class_->name()->body().begin());
  } else {
    return cast<GcField>(t, field);
  }
}

bool
classNeedsInit(Thread* t, GcClass* c)
{
  if (c->vmFlags() & NeedInitFlag) {
    if (c->vmFlags() & InitFlag) {
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
preInitClass(Thread* t, GcClass* c)
{
  int flags = c->vmFlags();

  loadMemoryBarrier();

  if (flags & NeedInitFlag) {
    PROTECT(t, c);
    ACQUIRE(t, t->m->classLock);

    if (c->vmFlags() & NeedInitFlag) {
      if (c->vmFlags() & InitFlag) {
        // If the class is currently being initialized and this the thread
        // which is initializing it, we should not try to initialize it
        // recursively.
        if (isInitializing(t, c)) {
          return false;
        }

        // some other thread is on the job - wait for it to finish.
        while (c->vmFlags() & InitFlag) {
          ENTER(t, Thread::IdleState);
          t->m->classLock->wait(t->systemThread, 0);
        }
      } else if (c->vmFlags() & InitErrorFlag) {
        throwNew(t, GcNoClassDefFoundError::Type, "%s",
                 c->name()->body().begin());
      } else {
        c->vmFlags() |= InitFlag;
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

  if (t->exception
      and instanceOf(t, type(t, GcException::Type), reinterpret_cast<object>(t->exception))) {
    classVmFlags(t, c) |= NeedInitFlag | InitErrorFlag;
    classVmFlags(t, c) &= ~InitFlag;

    GcThrowable* exception = t->exception;
    t->exception = 0;

    exception = makeThrowable
      (t, GcExceptionInInitializerError::Type, 0, 0, exception);
        
    set(t, exception, ExceptionInInitializerErrorException,
        exception->cause());

    throw_(t, exception);
  } else {
    classVmFlags(t, c) &= ~(NeedInitFlag | InitFlag);
  }
  t->m->classLock->notifyAll(t->systemThread);
}

void
initClass(Thread* t, GcClass* c)
{
  PROTECT(t, c);

  object super = reinterpret_cast<object>(c->super());
  if (super) {
    initClass(t, cast<GcClass>(t, super));
  }

  if (preInitClass(t, c)) {
    OBJECT_RESOURCE(t, c, postInitClass(t, c));

    GcMethod* initializer = classInitializer(t, c);

    if (initializer) {
      Thread::ClassInitStack stack(t, c);

      t->m->processor->invoke(t, initializer, 0);
    }
  }
}

GcClass*
resolveObjectArrayClass(Thread* t, GcClassLoader* loader, GcClass* elementClass)
{
  PROTECT(t, loader);
  PROTECT(t, elementClass);

  { GcClass* arrayClass = cast<GcClass>(t, getClassRuntimeData(t, elementClass)->arrayClass());
    if (arrayClass) {
      return arrayClass;
    }
  }

  GcByteArray* elementSpec = elementClass->name();
  PROTECT(t, elementSpec);

  GcByteArray* spec;
  if (elementSpec->body()[0] == '[') {
    spec = makeByteArray(t, elementSpec->length() + 1);
    spec->body()[0] = '[';
    memcpy(&spec->body()[1],
           elementSpec->body().begin(),
           elementSpec->length());
  } else {
    spec = makeByteArray(t, elementSpec->length() + 3);
    spec->body()[0] = '[';
    spec->body()[1] = 'L';
    memcpy(&spec->body()[2],
           elementSpec->body().begin(),
           elementSpec->length() - 1);
    spec->body()[elementSpec->length() + 1] = ';';
    spec->body()[elementSpec->length() + 2] = 0;
  }

  GcClass* arrayClass = resolveClass(t, loader, spec);

  set(t, getClassRuntimeData(t, elementClass), ClassRuntimeDataArrayClass, arrayClass);

  return arrayClass;
}

object
makeObjectArray(Thread* t, GcClass* elementClass, unsigned count)
{
  GcClass* arrayClass = resolveObjectArrayClass
    (t, elementClass->loader(), elementClass);

  PROTECT(t, arrayClass);

  object array = reinterpret_cast<object>(makeArray(t, count));
  setObjectClass(t, array, arrayClass);

  return array;
}

object
findFieldInClass(Thread* t, GcClass* class_, GcByteArray* name, GcByteArray* spec)
{
  return findInTable
    (t, class_->fieldTable(), name, spec, fieldName, fieldSpec);
}

object
findMethodInClass(Thread* t, GcClass* class_, GcByteArray* name, GcByteArray* spec)
{
  return findInTable
    (t, class_->methodTable(), name, spec, methodName, methodSpec);
}

object
findInHierarchyOrNull(Thread* t, GcClass* class_, GcByteArray* name, GcByteArray* spec,
                      object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*))
{
  GcClass* originalClass = class_;

  object o = 0;
  if ((class_->flags() & ACC_INTERFACE)
      and class_->virtualTable())
  {
    o = findInTable
      (t, class_->virtualTable(), name, spec, methodName, methodSpec);
  }

  if (o == 0) {
    for (; o == 0 and class_; class_ = class_->super()) {
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

  GcFinalizer* f = makeFinalizer(t, 0, function, 0, 0, 0);
  f->target() = target;
  f->next() = reinterpret_cast<object>(t->m->finalizers);
  t->m->finalizers = f;
}

GcMonitor*
objectMonitor(Thread* t, object o, bool createNew)
{
  assertT(t, t->state == Thread::ActiveState);

  object m = hashMapFind
    (t, cast<GcHashMap>(t, root(t, Machine::MonitorMap)), o, objectHash, objectEqual);

  if (m) {
    if (DebugMonitors) {
      fprintf(stderr, "found monitor %p for object %x\n", m, objectHash(t, o));
    }

    return cast<GcMonitor>(t, m);
  } else if (createNew) {
    PROTECT(t, o);
    PROTECT(t, m);

    { ENTER(t, Thread::ExclusiveState);

      m = hashMapFind
        (t, cast<GcHashMap>(t, root(t, Machine::MonitorMap)), o, objectHash, objectEqual);

      if (m) {
        if (DebugMonitors) {
          fprintf(stderr, "found monitor %p for object %x\n",
                  m, objectHash(t, o));
        }

        return cast<GcMonitor>(t, m);
      }

      object head = reinterpret_cast<object>(makeMonitorNode(t, 0, 0));
      m = reinterpret_cast<object>(makeMonitor(t, 0, 0, 0, head, head, 0));

      if (DebugMonitors) {
        fprintf(stderr, "made monitor %p for object %x\n", m,
                objectHash(t, o));
      }

      hashMapInsert(t, cast<GcHashMap>(t, root(t, Machine::MonitorMap)), o, m, objectHash);

      addFinalizer(t, o, removeMonitor);
    }

    return cast<GcMonitor>(t, m);
  } else {
    return 0;
  }
}

object
intern(Thread* t, object s)
{
  PROTECT(t, s);

  ACQUIRE(t, t->m->referenceLock);

  GcTriple* n = hashMapFindNode
    (t, cast<GcHashMap>(t, root(t, Machine::StringMap)), s, stringHash, stringEqual);

  if (n) {
    return jreferenceTarget(t, n->first());
  } else {
    hashMapInsert(t, cast<GcHashMap>(t, root(t, Machine::StringMap)), s, 0, stringHash);
    addFinalizer(t, s, removeString);
    return s;
  }
}

void
walk(Thread* t, Heap::Walker* w, object o, unsigned start)
{
  GcClass* class_ = cast<GcClass>(t, static_cast<object>(t->m->heap->follow(objectClass(t, o))));
  object objectMask = static_cast<object>
    (t->m->heap->follow(class_->objectMask()));

  bool more = true;

  if (objectMask) {
    unsigned fixedSize = class_->fixedSize();
    unsigned arrayElementSize = class_->arrayElementSize();
    unsigned arrayLength
      = (arrayElementSize ?
         fieldAtOffset<uintptr_t>(o, fixedSize - BytesPerWord) : 0);

    THREAD_RUNTIME_ARRAY(t, uint32_t, mask, intArrayLength(t, objectMask));
    memcpy(RUNTIME_ARRAY_BODY(mask), &intArrayBody(t, objectMask, 0),
           intArrayLength(t, objectMask) * 4);

    more = ::walk(t, w, RUNTIME_ARRAY_BODY(mask), fixedSize, arrayElementSize,
                  arrayLength, start);
  } else if (class_->vmFlags() & SingletonFlag) {
    unsigned length = singletonLength(t, o);
    if (length) {
      more = ::walk(t, w, singletonMask(t, cast<GcSingleton>(t, o)),
                    (singletonCount(t, cast<GcSingleton>(t, o)) + 2) * BytesPerWord, 0, 0, start);
    } else if (start == 0) {
      more = w->visit(0);
    }
  } else if (start == 0) {
    more = w->visit(0);
  }

  if (more and class_->vmFlags() & ContinuationFlag) {
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
printTrace(Thread* t, GcThrowable* exception)
{
  if (exception == 0) {
    exception = makeThrowable(t, GcNullPointerException::Type);
  }

  for (GcThrowable* e = exception; e; e = e->cause()) {
    if (e != exception) {
      logTrace(errorLog(t), "caused by: ");
    }

    logTrace(errorLog(t), "%s", objectClass(t, e)->name()->body().begin());

    if (e->message()) {
      GcString* m = e->message();
      THREAD_RUNTIME_ARRAY(t, char, message, m->length(t) + 1);
      stringChars(t, m, RUNTIME_ARRAY_BODY(message));
      logTrace(errorLog(t), ": %s\n", RUNTIME_ARRAY_BODY(message));
    } else {
      logTrace(errorLog(t), "\n");
    }

    object trace = e->trace();
    if (trace) {
      for (unsigned i = 0; i < objectArrayLength(t, trace); ++i) {
        object e = objectArrayBody(t, trace, i);
        const int8_t* class_ = &byteArrayBody
          (t, className(t, methodClass(t, traceElementMethod(t, e))), 0);
        const int8_t* method = &byteArrayBody
          (t, methodName(t, traceElementMethod(t, e)), 0);
        int line = t->m->processor->lineNumber
          (t, cast<GcMethod>(t, traceElementMethod(t, e)), traceElementIp(t, e));

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

    if (e == e->cause()) {
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
        assertT(t, trace);
      }

      object e = reinterpret_cast<object>(makeTraceElement(t, reinterpret_cast<object>(walker->method()), walker->ip()));
      assertT(t, index < objectArrayLength(t, trace));
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

    for (; finalizeList; finalizeList = reinterpret_cast<object>(finalizerQueueNext(t, finalizeList))) {
      finalizeObject(t, finalizerQueueTarget(t, finalizeList), "finalize");
    }

    for (; cleanList; cleanList = reinterpret_cast<object>(cleanerQueueNext(t, cleanList))) {
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
      if (false) abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, reinterpret_cast<const uint8_t*>(data), length);

  return ::parseUtf8(t, s, length);
}

object
parseUtf8(Thread* t, GcByteArray* array)
{
  for (unsigned i = 0; i < array->length() - 1; ++i) {
    if (array->body()[i] & 0x80) {
      goto slow_path;
    }
  }

  return reinterpret_cast<object>(array);

 slow_path:
  class Client: public Stream::Client {
   public:
    Client(Thread* t): t(t) { }

    virtual void handleError() {
      if (false) abort(t);
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

    MyStream(Thread* t, Client* client, GcByteArray* array):
      AbstractStream(client, array->length() - 1),
      array(array),
      protector(t, this)
    { }

    virtual void copy(uint8_t* dst, unsigned offset, unsigned size) {
      memcpy(dst, &array->body()[offset], size);
    }

    GcByteArray* array;
    MyProtector protector;
  } s(t, &client, array);

  return ::parseUtf8(t, s, array->length() - 1);
}

GcMethod*
getCaller(Thread* t, unsigned target, bool skipMethodInvoke)
{
  if (static_cast<int>(target) == -1) {
    target = 2;
  }

  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, unsigned target, bool skipMethodInvoke):
      t(t), method(0), count(0), target(target),
      skipMethodInvoke(skipMethodInvoke)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipMethodInvoke
          and walker->method()->class_()
              == type(t, GcJmethod::Type)
          and strcmp(walker->method()->name()->body().begin(),
                     reinterpret_cast<const int8_t*>("invoke")) == 0) {
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
    GcMethod* method;
    unsigned count;
    unsigned target;
    bool skipMethodInvoke;
    } v(t, target, skipMethodInvoke);

  t->m->processor->walkStack(t, &v);

  return v.method;
}

object
defineClass(Thread* t, GcClassLoader* loader, const uint8_t* buffer, unsigned length)
{
  PROTECT(t, loader);

  object c = reinterpret_cast<object>(parseClass(t, loader, buffer, length));
  
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

  saveLoadedClass(t, loader, cast<GcClass>(t, c));

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

  GcByteArray* spec = objectClass(t, array)->name();
  PROTECT(t, spec);

  GcByteArray* elementSpec = makeByteArray(t, spec->length() - 1);
  memcpy(elementSpec->body().begin(),
         &spec->body()[1],
         spec->length() - 1);

  GcClass* class_ = resolveClass
    (t, objectClass(t, array)->loader(), elementSpec);
  PROTECT(t, class_);

  for (int32_t i = 0; i < counts[index]; ++i) {
    object a = reinterpret_cast<object>(makeArray
      (t, ceilingDivide
       (counts[index + 1] * class_->arrayElementSize(), BytesPerWord)));
    arrayLength(t, a) = counts[index + 1];
    setObjectClass(t, a, class_);
    set(t, array, ArrayBody + (i * BytesPerWord), a);
    
    populateMultiArray(t, a, counts, index + 1, dimensions);
  }
}

object
interruptLock(Thread* t, GcThread* thread)
{
  object lock = thread->interruptLock();

  loadMemoryBarrier();

  if (lock == 0) {
    PROTECT(t, thread);
    ACQUIRE(t, t->m->referenceLock);

    if (thread->interruptLock() == 0) {
      object head = reinterpret_cast<object>(makeMonitorNode(t, 0, 0));
      object lock = reinterpret_cast<object>(makeMonitor(t, 0, 0, 0, head, head, 0));

      storeStoreMemoryBarrier();

      set(t, reinterpret_cast<object>(thread), ThreadInterruptLock, lock);
    }
  }
  
  return thread->interruptLock();
}

void
clearInterrupted(Thread* t)
{
  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
  t->javaThread->interrupted() = false;
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
}

void
threadInterrupt(Thread* t, GcThread* thread)
{
  PROTECT(t, thread);
  
  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, thread)));
  Thread* p = reinterpret_cast<Thread*>(thread->peer());
  if (p) {
    interrupt(t, p);
  }
  thread->interrupted() = true;
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, thread)));
}

bool
threadIsInterrupted(Thread* t, GcThread* thread, bool clear)
{
  PROTECT(t, thread);
  
  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, thread)));
  bool v = thread->interrupted();
  if (clear) {
    thread->interrupted() = false;
  }
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, thread)));
  return v;
}

void
noop()
{ }

#include "type-constructors.cpp"

} // namespace vm

// for debugging
AVIAN_EXPORT void
vmfPrintTrace(Thread* t, FILE* out)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, FILE* out): t(t), out(out) { }

    virtual bool visit(Processor::StackWalker* walker) {
      const int8_t* class_ = walker->method()->class_()->name()->body().begin();
      const int8_t* method = walker->method()->name()->body().begin();
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

AVIAN_EXPORT void
vmPrintTrace(Thread* t)
{
  vmfPrintTrace(t, stderr);
}

// also for debugging
AVIAN_EXPORT void*
vmAddressFromLine(Thread* t, object m, unsigned line)
{
  object code = methodCode(t, m);
  printf("code: %p\n", code);
  object lnt = reinterpret_cast<object>(codeLineNumberTable(t, code));
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
