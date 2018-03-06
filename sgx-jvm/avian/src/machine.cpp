/* Copyright (c) 2008-2015, Avian Contributors

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
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

using namespace vm;
using namespace avian::util;

namespace {

const bool DebugClassReader = false;

const unsigned NoByte = 0xFFFF;

#ifdef USE_ATOMIC_OPERATIONS
void atomicIncrement(uint32_t* p, int v)
{
  for (uint32_t old = *p; not atomicCompareAndSwap32(p, old, old + v);
       old = *p) {
  }
}
#endif

void join(Thread* t, Thread* o)
{
  if (t != o) {
    assertT(t, o->state != Thread::JoinedState);
    assertT(t, (o->getFlags() & Thread::SystemFlag) == 0);
    if (o->getFlags() & Thread::JoinFlag) {
      o->systemThread->join();
    }
    o->state = Thread::JoinedState;
  }
}

#ifndef NDEBUG

bool find(Thread* t, Thread* o)
{
  return (t == o) or (t->peer and find(t->peer, o))
         or (t->child and find(t->child, o));
}

unsigned count(Thread* t, Thread* o)
{
  unsigned c = 0;

  if (t != o)
    ++c;
  if (t->peer)
    c += count(t->peer, o);
  if (t->child)
    c += count(t->child, o);

  return c;
}

Thread** fill(Thread* t, Thread* o, Thread** array)
{
  if (t != o)
    *(array++) = t;
  if (t->peer)
    array = fill(t->peer, o, array);
  if (t->child)
    array = fill(t->child, o, array);

  return array;
}

#endif  // not NDEBUG

void dispose(Thread* t, Thread* o, bool remove)
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

void visitAll(Thread* m, Thread* o, void (*visit)(Thread*, Thread*))
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    visitAll(m, child, visit);
  }

  visit(m, o);
}

void disposeNoRemove(Thread* m, Thread* o)
{
  dispose(m, o, false);
}

void interruptDaemon(Thread* m, Thread* o)
{
  if (o->getFlags() & Thread::DaemonFlag) {
    interrupt(m, o);
  }
}

void turnOffTheLights(Thread* t)
{
  expect(t, t->m->liveCount == 1);

  visitAll(t, t->m->rootThread, join);

  enter(t, Thread::ExitState);

  {
    GcFinalizer* p = 0;
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

  if (GcArray* files = roots(t)->virtualFiles()) {
    PROTECT(t, files);
    for (unsigned i = 0; i < files->length(); ++i) {
      object region = files->body()[i];
      if (region) {
        static_cast<System::Region*>(cast<GcRegion>(t, region)->region())
            ->dispose();
      }
    }
  }

  for (GcFinder* p = roots(t)->virtualFileFinders(); p; p = p->next()) {
    static_cast<Finder*>(p->finder())->dispose();
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

void killZombies(Thread* t, Thread* o)
{
  for (Thread* p = o->child; p;) {
    Thread* child = p;
    p = p->peer;
    killZombies(t, child);
  }

  if ((o->getFlags() & Thread::SystemFlag) == 0) {
    switch (o->state) {
    case Thread::ZombieState:
      join(t, o);
    // fall through

    case Thread::JoinedState:
      dispose(t, o, true);

    default:
      break;
    }
  }
}

unsigned footprint(Thread* t)
{
  expect(t, t->criticalLevel == 0);

  unsigned n = t->heapOffset + t->heapIndex + t->backupHeapIndex;

  for (Thread* c = t->child; c; c = c->peer) {
    n += footprint(c);
  }

  return n;
}

void visitRoots(Thread* t, Heap::Visitor* v)
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

bool walk(Thread*,
          Heap::Walker* w,
          uint32_t* mask,
          unsigned fixedSize,
          unsigned arrayElementSize,
          unsigned arrayLength,
          unsigned start)
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
          if (not w->visit(fixedSizeInWords + (i * arrayElementSizeInWords)
                           + j)) {
            return false;
          }
        }
      }
    }
  }

  return true;
}

object findInInterfaces(
    Thread* t,
    GcClass* class_,
    GcByteArray* name,
    GcByteArray* spec,
    object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*))
{
  object result = 0;
  if (GcArray* itable = cast<GcArray>(t, class_->interfaceTable())) {
    PROTECT(t, itable);
    for (unsigned i = 0; i < itable->length() and result == 0; i += 2) {
      result = find(t, cast<GcClass>(t, itable->body()[i]), name, spec);
    }
  }
  return result;
}

void finalizerTargetUnreachable(Thread* t, Heap::Visitor* v, GcFinalizer** p)
{
  v->visit(&(*p)->target());

  GcFinalizer* finalizer = *p;
  *p = cast<GcFinalizer>(t, finalizer->next());

  void (*function)(Thread*, object);
  memcpy(&function, &finalizer->finalize(), BytesPerWord);

  if (function) {
    // TODO: use set() here?
    finalizer->next() = t->m->finalizeQueue;
    t->m->finalizeQueue = finalizer;
  } else {
    finalizer->setQueueTarget(t, finalizer->target());
    finalizer->setQueueNext(t, roots(t)->objectsToFinalize());
    roots(t)->setObjectsToFinalize(t, finalizer);
  }
}

void referenceTargetUnreachable(Thread* t, Heap::Visitor* v, GcJreference** p)
{
  if (DebugReferences) {
    fprintf(
        stderr, "target %p unreachable for reference %p\n", (*p)->target(), *p);
  }

  v->visit(p);
  (*p)->target() = 0;

  if (objectClass(t, *p) == type(t, GcCleaner::Type)) {
    // In openjdk, sun/misc/Cleaner extends PhantomReference
    GcCleaner* cleaner = (*p)->as<GcCleaner>(t);

    *p = cast<GcJreference>(t, (*p)->vmNext());

    cleaner->setQueueNext(t, roots(t)->objectsToClean());
    roots(t)->setObjectsToClean(t, cleaner);
  } else {
    if ((*p)->queue()
        and t->m->heap->status((*p)->queue()) != Heap::Unreachable) {
      // queue is reachable - add the reference

      v->visit(&(*p)->queue());

      GcReferenceQueue* q = (*p)->queue();

      if (q->front()) {
        (*p)->setJNext(t, q->front());
      } else {
        (*p)->setJNext(t, *p);
      }
      q->setFront(t, *p);

      (*p)->queue() = 0;
    }

    *p = cast<GcJreference>(t, (*p)->vmNext());
  }
}

void referenceUnreachable(Thread* t, Heap::Visitor* v, GcJreference** p)
{
  GcJreference* r = t->m->heap->follow(*p);

  if (DebugReferences) {
    fprintf(stderr, "reference %p unreachable (target %p)\n", *p, r->target());
  }

  if (r->queue() and t->m->heap->status(r->queue()) != Heap::Unreachable) {
    // queue is reachable - add the reference
    referenceTargetUnreachable(t, v, p);
  } else {
    *p = cast<GcJreference>(t, (*p)->vmNext());
  }
}

void referenceTargetReachable(Thread* t, Heap::Visitor* v, GcJreference** p)
{
  if (DebugReferences) {
    fprintf(
        stderr, "target %p reachable for reference %p\n", (*p)->target(), *p);
  }

  v->visit(p);
  v->visit(&(*p)->target());

  if (t->m->heap->status((*p)->queue()) == Heap::Unreachable) {
    (*p)->queue() = 0;
  } else {
    v->visit(&(*p)->queue());
  }
}

bool isFinalizable(Thread* t, object o)
{
  return t->m->heap->status(o) == Heap::Unreachable
         and (t->m->heap->follow(objectClass(t, o))->vmFlags()
              & HasFinalizerFlag);
}

void clearTargetIfFinalizable(Thread* t, GcJreference* r)
{
  if (isFinalizable(t, t->m->heap->follow(r->target()))) {
    r->target() = 0;
  }
}

void postVisit(Thread* t, Heap::Visitor* v)
{
  Machine* m = t->m;
  bool major = m->heap->collectionType() == Heap::MajorCollection;

  assertT(t, m->finalizeQueue == 0);

  m->heap->postVisit();

  for (GcJreference* p = m->weakReferences; p;) {
    GcJreference* r = m->heap->follow(p);
    p = cast<GcJreference>(t, r->vmNext());
    clearTargetIfFinalizable(t, r);
  }

  if (major) {
    for (GcJreference* p = m->tenuredWeakReferences; p;) {
      GcJreference* r = m->heap->follow(p);
      p = cast<GcJreference>(t, r->vmNext());
      clearTargetIfFinalizable(t, r);
    }
  }

  for (Reference* r = m->jniReferences; r; r = r->next) {
    if (r->weak
        and isFinalizable(t,
                          static_cast<object>(t->m->heap->follow(r->target)))) {
      r->target = 0;
    }
  }

  GcFinalizer* firstNewTenuredFinalizer = 0;
  GcFinalizer* lastNewTenuredFinalizer = 0;

  {
    object unreachable = 0;
    for (GcFinalizer** p = &(m->finalizers); *p;) {
      v->visit(p);

      if (m->heap->status((*p)->target()) == Heap::Unreachable) {
        GcFinalizer* finalizer = *p;
        *p = cast<GcFinalizer>(t, finalizer->next());

        finalizer->next() = unreachable;
        unreachable = finalizer;
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
          lastNewTenuredFinalizer = *p;
        }

        GcFinalizer* finalizer = *p;
        *p = cast<GcFinalizer>(t, finalizer->next());
        finalizer->next() = firstNewTenuredFinalizer;
        firstNewTenuredFinalizer = finalizer;
      } else {
        p = reinterpret_cast<GcFinalizer**>(&(*p)->next());
      }
    }

    for (object* p = &unreachable; *p;) {
      // target is unreachable - queue it up for finalization
      finalizerTargetUnreachable(t, v, reinterpret_cast<GcFinalizer**>(p));
    }
  }

  GcJreference* firstNewTenuredWeakReference = 0;
  GcJreference* lastNewTenuredWeakReference = 0;

  for (GcJreference** p = &(m->weakReferences); *p;) {
    if (m->heap->status(*p) == Heap::Unreachable) {
      // reference is unreachable
      referenceUnreachable(t, v, p);
    } else if (m->heap->status(m->heap->follow(*p)->target())
               == Heap::Unreachable) {
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

        GcJreference* reference = (*p);
        *p = cast<GcJreference>(t, reference->vmNext());
        reference->vmNext() = firstNewTenuredWeakReference;
        firstNewTenuredWeakReference = reference;
      } else {
        p = reinterpret_cast<GcJreference**>(&(*p)->vmNext());
      }
    }
  }

  if (major) {
    {
      object unreachable = 0;
      for (GcFinalizer** p = &(m->tenuredFinalizers); *p;) {
        v->visit(p);

        if (m->heap->status((*p)->target()) == Heap::Unreachable) {
          GcFinalizer* finalizer = *p;
          *p = cast<GcFinalizer>(t, finalizer->next());

          finalizer->next() = unreachable;
          unreachable = finalizer;
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
        finalizerTargetUnreachable(t, v, reinterpret_cast<GcFinalizer**>(p));
      }
    }

    for (GcJreference** p = &(m->tenuredWeakReferences); *p;) {
      if (m->heap->status(*p) == Heap::Unreachable) {
        // reference is unreachable
        referenceUnreachable(t, v, reinterpret_cast<GcJreference**>(p));
      } else if (m->heap->status(m->heap->follow(*p)->target())
                 == Heap::Unreachable) {
        // target is unreachable
        referenceTargetUnreachable(t, v, reinterpret_cast<GcJreference**>(p));
      } else {
        // both reference and target are reachable
        referenceTargetReachable(t, v, reinterpret_cast<GcJreference**>(p));
        p = reinterpret_cast<GcJreference**>(&(*p)->vmNext());
      }
    }
  }

  if (lastNewTenuredFinalizer) {
    lastNewTenuredFinalizer->next() = m->tenuredFinalizers;
    m->tenuredFinalizers = firstNewTenuredFinalizer;
  }

  if (lastNewTenuredWeakReference) {
    lastNewTenuredWeakReference->vmNext() = m->tenuredWeakReferences;
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

void postCollect(Thread* t)
{
#ifdef VM_STRESS
  t->m->heap->free(t->defaultHeap, ThreadHeapSizeInBytes);
  t->defaultHeap
      = static_cast<uintptr_t*>(t->m->heap->allocate(ThreadHeapSizeInBytes));
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

  if (t->getFlags() & Thread::UseBackupHeapFlag) {
    memset(t->backupHeap, 0, ThreadBackupHeapSizeInBytes);

    t->clearFlag(Thread::UseBackupHeapFlag);
    t->backupHeapIndex = 0;
  }

  for (Thread* c = t->child; c; c = c->peer) {
    postCollect(c);
  }
}

uint64_t invoke(Thread* t, uintptr_t* arguments)
{
  GcMethod* m = cast<GcMethod>(t, *reinterpret_cast<object*>(arguments[0]));
  object o = *reinterpret_cast<object*>(arguments[1]);

  t->m->processor->invoke(t, m, o);

  return 1;
}

void finalizeObject(Thread* t, object o, const char* name)
{
  for (GcClass* c = objectClass(t, o); c; c = c->super()) {
    GcArray* mtable = cast<GcArray>(t, c->methodTable());
    for (unsigned i = 0; i < mtable->length(); ++i) {
      GcMethod* m = cast<GcMethod>(t, mtable->body()[i]);

      if (vm::strcmp(reinterpret_cast<const int8_t*>(name),
                     m->name()->body().begin()) == 0
          and vm::strcmp(reinterpret_cast<const int8_t*>("()V"),
                         m->spec()->body().begin()) == 0) {
        PROTECT(t, m);
        PROTECT(t, o);

        uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(&m),
                                 reinterpret_cast<uintptr_t>(&o)};

        run(t, invoke, arguments);

        t->exception = 0;
        return;
      }
    }
  }
  abort(t);
}

unsigned readByte(AbstractStream& s, unsigned* value)
{
  if (*value == NoByte) {
    return s.read1();
  } else {
    unsigned r = *value;
    *value = NoByte;
    return r;
  }
}

GcCharArray* parseUtf8NonAscii(Thread* t,
                               AbstractStream& s,
                               GcByteArray* bytesSoFar,
                               unsigned byteCount,
                               unsigned sourceIndex,
                               unsigned byteA,
                               unsigned byteB)
{
  PROTECT(t, bytesSoFar);

  unsigned length = bytesSoFar->length() - 1;
  GcCharArray* value = makeCharArray(t, length + 1);

  unsigned vi = 0;
  for (; vi < byteCount; ++vi) {
    value->body()[vi] = bytesSoFar->body()[vi];
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
        value->body()[vi++] = ((a & 0xf) << 12) | ((b & 0x3f) << 6)
                              | (c & 0x3f);
      } else {
        // 2 bytes
        ++si;
        assertT(t, si < length);
        unsigned b = readByte(s, &byteB);

        if (a == 0xC0 and b == 0x80) {
          value->body()[vi++] = 0;
        } else {
          value->body()[vi++] = ((a & 0x1f) << 6) | (b & 0x3f);
        }
      }
    } else {
      value->body()[vi++] = a;
    }
  }

  if (vi < length) {
    PROTECT(t, value);

    GcCharArray* v = makeCharArray(t, vi + 1);
    memcpy(v->body().begin(), value->body().begin(), vi * 2);
    value = v;
  }

  return value;
}

object parseUtf8(Thread* t, AbstractStream& s, unsigned length)
{
  GcByteArray* value = makeByteArray(t, length + 1);
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
          ++si;
          assertT(t, si < length);
          value->body()[vi++] = 0;
        } else {
          return parseUtf8NonAscii(t, s, value, vi, si, a, b);
        }
      }
    } else {
      value->body()[vi++] = a;
    }
  }

  if (vi < length) {
    PROTECT(t, value);

    GcByteArray* v = makeByteArray(t, vi + 1);
    memcpy(v->body().begin(), value->body().begin(), vi);
    value = v;
  }

  return value;
}

GcByteArray* makeByteArray(Thread* t, Stream& s, unsigned length)
{
  GcByteArray* value = makeByteArray(t, length + 1);
  s.read(reinterpret_cast<uint8_t*>(value->body().begin()), length);
  return value;
}

void removeByteArray(Thread* t, object o)
{
  hashMapRemove(t, roots(t)->byteArrayMap(), o, byteArrayHash, objectEqual);
}

GcByteArray* internByteArray(Thread* t, GcByteArray* array)
{
  PROTECT(t, array);

  ACQUIRE(t, t->m->referenceLock);

  GcTriple* n = hashMapFindNode(
      t, roots(t)->byteArrayMap(), array, byteArrayHash, byteArrayEqual);
  if (n) {
    return cast<GcByteArray>(t, cast<GcJreference>(t, n->first())->target());
  } else {
    hashMapInsert(t, roots(t)->byteArrayMap(), array, 0, byteArrayHash);
    addFinalizer(t, array, removeByteArray);
    return array;
  }
}

unsigned parsePoolEntry(Thread* t,
                        Stream& s,
                        uint32_t* index,
                        GcSingleton* pool,
                        unsigned i)
{
  PROTECT(t, pool);

  s.setPosition(index[i]);

  switch (s.read1()) {
  case CONSTANT_Integer:
  case CONSTANT_Float: {
    uint32_t v = s.read4();
    singletonValue(t, pool, i) = v;

    if (DebugClassReader) {
      fprintf(stderr, "    consts[%d] = int/float 0x%x\n", i, v);
    }
  }
    return 1;

  case CONSTANT_Long:
  case CONSTANT_Double: {
    uint64_t v = s.read8();
    memcpy(&singletonValue(t, pool, i), &v, 8);

    if (DebugClassReader) {
      fprintf(stderr, "    consts[%d] = long/double <todo>\n", i);
    }
  }
    return 2;

  case CONSTANT_Utf8: {
    if (singletonObject(t, pool, i) == 0) {
      GcByteArray* value = internByteArray(t, makeByteArray(t, s, s.read2()));
      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));

      if (DebugClassReader) {
        fprintf(stderr, "    consts[%d] = utf8 %s\n", i, value->body().begin());
      }
    }
  }
    return 1;

  case CONSTANT_Class: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);

      GcReference* value = makeReference(
          t, 0, 0, cast<GcByteArray>(t, singletonObject(t, pool, si)), 0);
      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));

      if (DebugClassReader) {
        fprintf(stderr, "    consts[%d] = class <todo>\n", i);
      }
    }
  }
    return 1;

  case CONSTANT_String: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);

      object value
          = parseUtf8(t, cast<GcByteArray>(t, singletonObject(t, pool, si)));
      value = t->m->classpath->makeString(
          t, value, 0, fieldAtOffset<uintptr_t>(value, BytesPerWord) - 1);
      value = intern(t, value);
      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));

      if (DebugClassReader) {
        fprintf(stderr, "    consts[%d] = string <todo>\n", i);
      }
    }
  }
    return 1;

  case CONSTANT_NameAndType: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned ni = s.read2() - 1;
      unsigned ti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ni);
      parsePoolEntry(t, s, index, pool, ti);

      GcByteArray* name = cast<GcByteArray>(t, singletonObject(t, pool, ni));
      GcByteArray* type = cast<GcByteArray>(t, singletonObject(t, pool, ti));
      GcPair* value = makePair(t, name, type);
      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));

      if (DebugClassReader) {
        fprintf(stderr,
                "    consts[%d] = nameAndType %s%s\n",
                i,
                name->body().begin(),
                type->body().begin());
      }
    }
  }
    return 1;

  case CONSTANT_Fieldref:
  case CONSTANT_Methodref:
  case CONSTANT_InterfaceMethodref: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned ci = s.read2() - 1;
      unsigned nti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ci);
      parsePoolEntry(t, s, index, pool, nti);

      GcByteArray* className
          = cast<GcReference>(t, singletonObject(t, pool, ci))->name();
      GcPair* nameAndType = cast<GcPair>(t, singletonObject(t, pool, nti));

      object value = makeReference(t,
                                   0,
                                   className,
                                   cast<GcByteArray>(t, nameAndType->first()),
                                   cast<GcByteArray>(t, nameAndType->second()));
      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));

      if (DebugClassReader) {
        fprintf(stderr,
                "    consts[%d] = method %s.%s%s\n",
                i,
                className->body().begin(),
                cast<GcByteArray>(t, nameAndType->first())->body().begin(),
                cast<GcByteArray>(t, nameAndType->second())->body().begin());
      }
    }
  }
    return 1;

  case CONSTANT_MethodHandle:
    if (singletonObject(t, pool, i) == 0) {
      unsigned kind = s.read1();
      unsigned ri = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ri);

      GcReference* value = cast<GcReference>(t, singletonObject(t, pool, ri));

      if (DebugClassReader) {
        fprintf(stderr,
                "   consts[%d] = method handle %d %s.%s%s\n",
                i,
                kind,
                value->class_()->body().begin(),
                value->name()->body().begin(),
                value->spec()->body().begin());
      }

      value = makeReference(
          t, kind, value->class_(), value->name(), value->spec());

      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));
    }
    return 1;

  case CONSTANT_MethodType:
    if (singletonObject(t, pool, i) == 0) {
      unsigned ni = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, ni);

      pool->setBodyElement(
          t, i, reinterpret_cast<uintptr_t>(singletonObject(t, pool, ni)));
    }
    return 1;

  case CONSTANT_InvokeDynamic:
    if (singletonObject(t, pool, i) == 0) {
      unsigned bootstrap = s.read2();
      unsigned nti = s.read2() - 1;

      parsePoolEntry(t, s, index, pool, nti);

      GcPair* nameAndType = cast<GcPair>(t, singletonObject(t, pool, nti));

      const char* specString = reinterpret_cast<const char*>(
          cast<GcByteArray>(t, nameAndType->second())->body().begin());

      unsigned parameterCount;
      unsigned parameterFootprint;
      unsigned returnCode;
      scanMethodSpec(t,
                     specString,
                     true,
                     &parameterCount,
                     &parameterFootprint,
                     &returnCode);

      GcMethod* template_
          = makeMethod(t,
                       0,
                       returnCode,
                       parameterCount,
                       parameterFootprint,
                       ACC_STATIC,
                       0,
                       0,
                       0,
                       cast<GcByteArray>(t, nameAndType->first()),
                       cast<GcByteArray>(t, nameAndType->second()),
                       0,
                       0,
                       0);

      object value = reinterpret_cast<object>(
          makeInvocation(t, bootstrap, -1, 0, pool, template_, 0));

      pool->setBodyElement(t, i, reinterpret_cast<uintptr_t>(value));
    }
    return 1;

  default:
    abort(t);
  }
}

GcSingleton* parsePool(Thread* t, Stream& s)
{
  unsigned count = s.read2() - 1;
  GcSingleton* pool = makeSingletonOfSize(t, count + poolMaskSize(count));
  PROTECT(t, pool);

  if (DebugClassReader) {
    fprintf(stderr, "  const pool entries %d\n", count);
  }

  if (count) {
    uint32_t* index = static_cast<uint32_t*>(t->m->heap->allocate(count * 4));

    THREAD_RESOURCE2(t,
                     uint32_t*,
                     index,
                     unsigned,
                     count,
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
        ++i;
        break;

      case CONSTANT_Double:
        singletonSetBit(t, pool, count, i);
        singletonSetBit(t, pool, count, i + 1);
        s.skip(8);
        ++i;
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

      default:
        abort(t);
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

void addInterfaces(Thread* t, GcClass* class_, GcHashMap* map)
{
  GcArray* table = cast<GcArray>(t, class_->interfaceTable());
  if (table) {
    unsigned increment = 2;
    if (class_->flags() & ACC_INTERFACE) {
      increment = 1;
    }

    PROTECT(t, map);
    PROTECT(t, table);

    for (unsigned i = 0; i < table->length(); i += increment) {
      GcClass* interface = cast<GcClass>(t, table->body()[i]);
      GcByteArray* name = interface->name();
      hashMapInsertMaybe(
          t, map, name, interface, byteArrayHash, byteArrayEqual);
    }
  }
}

GcClassAddendum* getClassAddendum(Thread* t, GcClass* class_, GcSingleton* pool)
{
  GcClassAddendum* addendum = class_->addendum();
  if (addendum == 0) {
    PROTECT(t, class_);

    addendum = makeClassAddendum(t, pool, 0, 0, 0, 0, -1, 0, 0, 0, 0);
    setField(t, class_, ClassAddendum, addendum);
  }
  return addendum;
}

void parseInterfaceTable(Thread* t,
                         Stream& s,
                         GcClass* class_,
                         GcSingleton* pool,
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
  GcArray* table = 0;
  PROTECT(t, table);

  if (count) {
    table = makeArray(t, count);

    GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
    addendum->setInterfaceTable(t, table);
  }

  for (unsigned i = 0; i < count; ++i) {
    GcByteArray* name
        = cast<GcReference>(t, singletonObject(t, pool, s.read2() - 1))->name();
    PROTECT(t, name);

    GcClass* interface = resolveClass(
        t, class_->loader(), name, true, throwType);

    PROTECT(t, interface);

    table->setBodyElement(t, i, interface);

    hashMapInsertMaybe(t, map, name, interface, byteArrayHash, byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  GcArray* interfaceTable = 0;
  if (map->size()) {
    unsigned length = map->size();
    if ((class_->flags() & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = makeArray(t, length);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    for (HashMapIterator it(t, map); it.hasMore();) {
      GcClass* interface = cast<GcClass>(t, it.next()->second());

      interfaceTable->setBodyElement(t, i, interface);
      ++i;

      if ((class_->flags() & ACC_INTERFACE) == 0) {
        if (GcArray* vt = cast<GcArray>(t, interface->virtualTable())) {
          PROTECT(t, vt);
          // we'll fill in this table in parseMethodTable():
          GcArray* vtable = makeArray(t, vt->length());

          interfaceTable->setBodyElement(t, i, vtable);
        }

        ++i;
      }
    }
  }

  class_->setInterfaceTable(t, interfaceTable);
}

void parseFieldTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool)
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

    GcArray* fieldTable = makeArray(t, count);
    PROTECT(t, fieldTable);

    GcIntArray* staticValueTable = makeIntArray(t, count);
    PROTECT(t, staticValueTable);

    GcFieldAddendum* addendum = 0;
    PROTECT(t, addendum);

    THREAD_RUNTIME_ARRAY(t, uint8_t, staticTypes, count);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      unsigned value = 0;

      addendum = 0;

      unsigned code = fieldCode(
          t,
          cast<GcByteArray>(t, singletonObject(t, pool, spec - 1))->body()[0]);

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        GcByteArray* name
            = cast<GcByteArray>(t, singletonObject(t, pool, s.read2() - 1));
        unsigned length = s.read4();

        if (vm::strcmp(reinterpret_cast<const int8_t*>("ConstantValue"),
                       name->body().begin()) == 0) {
          value = s.read2();
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                              name->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeFieldAddendum(t, pool, 0, 0);
          }

          addendum->setSignature(t, singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>(
                                  "RuntimeVisibleAnnotations"),
                              name->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeFieldAddendum(t, pool, 0, 0);
          }

          GcByteArray* body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(body->body().begin()), length);

          addendum->setAnnotationTable(t, body);
        } else {
          s.skip(length);
        }
      }

      GcField* field
          = makeField(t,
                      0,  // vm flags
                      code,
                      flags,
                      0,  // offset
                      0,  // native ID
                      cast<GcByteArray>(t, singletonObject(t, pool, name - 1)),
                      cast<GcByteArray>(t, singletonObject(t, pool, spec - 1)),
                      addendum,
                      class_);

      unsigned size = fieldSize(t, code);
      if (flags & ACC_STATIC) {
        staticOffset = pad(staticOffset, size);

        field->offset() = staticOffset;

        staticOffset += size;

        staticValueTable->body()[staticCount] = value;

        RUNTIME_ARRAY_BODY(staticTypes)[staticCount++] = code;
      } else {
        if (flags & ACC_FINAL) {
          class_->vmFlags() |= HasFinalMemberFlag;
        }

        memberOffset = pad(memberOffset, size);

        field->offset() = memberOffset;

        memberOffset += size;
      }

      fieldTable->setBodyElement(t, i, field);
    }

    class_->setFieldTable(t, fieldTable);

    if (staticCount) {
      unsigned footprint
          = ceilingDivide(staticOffset - (BytesPerWord * 2), BytesPerWord);
      GcSingleton* staticTable = makeSingletonOfSize(t, footprint);

      uint8_t* body = reinterpret_cast<uint8_t*>(staticTable->body().begin());

      memcpy(body, &class_, BytesPerWord);
      singletonMarkObject(t, staticTable, 0);

      for (unsigned i = 0, offset = BytesPerWord; i < staticCount; ++i) {
        unsigned size = fieldSize(t, RUNTIME_ARRAY_BODY(staticTypes)[i]);
        offset = pad(offset, size);

        unsigned value = staticValueTable->body()[i];
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

          default:
            abort(t);
          }
        }

        if (RUNTIME_ARRAY_BODY(staticTypes)[i] == ObjectField) {
          singletonMarkObject(t, staticTable, offset / BytesPerWord);
        }

        offset += size;
      }

      class_->setStaticTable(t, staticTable);
    }
  }

  class_->fixedSize() = memberOffset;

  if (class_->super() and memberOffset == class_->super()->fixedSize()) {
    class_->setObjectMask(t, class_->super()->objectMask());
  } else {
    GcIntArray* mask = makeIntArray(
        t, ceilingDivide(class_->fixedSize(), 32 * BytesPerWord));
    mask->body()[0] = 1;

    GcIntArray* superMask = 0;
    if (class_->super()) {
      superMask = class_->super()->objectMask();
      if (superMask) {
        memcpy(
            mask->body().begin(),
            superMask->body().begin(),
            ceilingDivide(class_->super()->fixedSize(), 32 * BytesPerWord) * 4);
      }
    }

    bool sawReferenceField = false;
    GcArray* fieldTable = cast<GcArray>(t, class_->fieldTable());
    if (fieldTable) {
      for (int i = fieldTable->length() - 1; i >= 0; --i) {
        GcField* field = cast<GcField>(t, fieldTable->body()[i]);
        if ((field->flags() & ACC_STATIC) == 0
            and field->code() == ObjectField) {
          unsigned index = field->offset() / BytesPerWord;
          mask->body()[index / 32] |= 1 << (index % 32);
          sawReferenceField = true;
        }
      }
    }

    if (superMask or sawReferenceField) {
      class_->setObjectMask(t, mask);
    }
  }
}

uint16_t read16(uint8_t* code, unsigned& ip)
{
  uint16_t a = code[ip++];
  uint16_t b = code[ip++];
  return (a << 8) | b;
}

uint32_t read32(uint8_t* code, unsigned& ip)
{
  uint32_t b = code[ip++];
  uint32_t a = code[ip++];
  uint32_t c = code[ip++];
  uint32_t d = code[ip++];
  return (a << 24) | (b << 16) | (c << 8) | d;
}

void disassembleCode(const char* prefix, uint8_t* code, unsigned length)
{
  unsigned ip = 0;

  while (ip < length) {
    unsigned instr;
    fprintf(stderr, "%s%x:\t", prefix, ip);
    switch (instr = code[ip++]) {
    case aaload:
      fprintf(stderr, "aaload\n");
      break;
    case aastore:
      fprintf(stderr, "aastore\n");
      break;

    case aconst_null:
      fprintf(stderr, "aconst_null\n");
      break;

    case aload:
      fprintf(stderr, "aload %02x\n", code[ip++]);
      break;
    case aload_0:
      fprintf(stderr, "aload_0\n");
      break;
    case aload_1:
      fprintf(stderr, "aload_1\n");
      break;
    case aload_2:
      fprintf(stderr, "aload_2\n");
      break;
    case aload_3:
      fprintf(stderr, "aload_3\n");
      break;

    case anewarray:
      fprintf(stderr, "anewarray %04x\n", read16(code, ip));
      break;
    case areturn:
      fprintf(stderr, "areturn\n");
      break;
    case arraylength:
      fprintf(stderr, "arraylength\n");
      break;

    case astore:
      fprintf(stderr, "astore %02x\n", code[ip++]);
      break;
    case astore_0:
      fprintf(stderr, "astore_0\n");
      break;
    case astore_1:
      fprintf(stderr, "astore_1\n");
      break;
    case astore_2:
      fprintf(stderr, "astore_2\n");
      break;
    case astore_3:
      fprintf(stderr, "astore_3\n");
      break;

    case athrow:
      fprintf(stderr, "athrow\n");
      break;
    case baload:
      fprintf(stderr, "baload\n");
      break;
    case bastore:
      fprintf(stderr, "bastore\n");
      break;

    case bipush:
      fprintf(stderr, "bipush %02x\n", code[ip++]);
      break;
    case caload:
      fprintf(stderr, "caload\n");
      break;
    case castore:
      fprintf(stderr, "castore\n");
      break;
    case checkcast:
      fprintf(stderr, "checkcast %04x\n", read16(code, ip));
      break;
    case d2f:
      fprintf(stderr, "d2f\n");
      break;
    case d2i:
      fprintf(stderr, "d2i\n");
      break;
    case d2l:
      fprintf(stderr, "d2l\n");
      break;
    case dadd:
      fprintf(stderr, "dadd\n");
      break;
    case daload:
      fprintf(stderr, "daload\n");
      break;
    case dastore:
      fprintf(stderr, "dastore\n");
      break;
    case dcmpg:
      fprintf(stderr, "dcmpg\n");
      break;
    case dcmpl:
      fprintf(stderr, "dcmpl\n");
      break;
    case dconst_0:
      fprintf(stderr, "dconst_0\n");
      break;
    case dconst_1:
      fprintf(stderr, "dconst_1\n");
      break;
    case ddiv:
      fprintf(stderr, "ddiv\n");
      break;
    case dmul:
      fprintf(stderr, "dmul\n");
      break;
    case dneg:
      fprintf(stderr, "dneg\n");
      break;
    case vm::drem:
      fprintf(stderr, "drem\n");
      break;
    case dsub:
      fprintf(stderr, "dsub\n");
      break;
    case vm::dup:
      fprintf(stderr, "dup\n");
      break;
    case dup_x1:
      fprintf(stderr, "dup_x1\n");
      break;
    case dup_x2:
      fprintf(stderr, "dup_x2\n");
      break;
    case vm::dup2:
      fprintf(stderr, "dup2\n");
      break;
    case dup2_x1:
      fprintf(stderr, "dup2_x1\n");
      break;
    case dup2_x2:
      fprintf(stderr, "dup2_x2\n");
      break;
    case f2d:
      fprintf(stderr, "f2d\n");
      break;
    case f2i:
      fprintf(stderr, "f2i\n");
      break;
    case f2l:
      fprintf(stderr, "f2l\n");
      break;
    case fadd:
      fprintf(stderr, "fadd\n");
      break;
    case faload:
      fprintf(stderr, "faload\n");
      break;
    case fastore:
      fprintf(stderr, "fastore\n");
      break;
    case fcmpg:
      fprintf(stderr, "fcmpg\n");
      break;
    case fcmpl:
      fprintf(stderr, "fcmpl\n");
      break;
    case fconst_0:
      fprintf(stderr, "fconst_0\n");
      break;
    case fconst_1:
      fprintf(stderr, "fconst_1\n");
      break;
    case fconst_2:
      fprintf(stderr, "fconst_2\n");
      break;
    case fdiv:
      fprintf(stderr, "fdiv\n");
      break;
    case fmul:
      fprintf(stderr, "fmul\n");
      break;
    case fneg:
      fprintf(stderr, "fneg\n");
      break;
    case frem:
      fprintf(stderr, "frem\n");
      break;
    case fsub:
      fprintf(stderr, "fsub\n");
      break;

    case getfield:
      fprintf(stderr, "getfield %04x\n", read16(code, ip));
      break;
    case getstatic:
      fprintf(stderr, "getstatic %04x\n", read16(code, ip));
      break;
    case goto_: {
      int16_t offset = read16(code, ip);
      fprintf(stderr, "goto %04x\n", offset + ip - 3);
    } break;
    case goto_w: {
      int32_t offset = read32(code, ip);
      fprintf(stderr, "goto_w %08x\n", offset + ip - 5);
    } break;

    case i2b:
      fprintf(stderr, "i2b\n");
      break;
    case i2c:
      fprintf(stderr, "i2c\n");
      break;
    case i2d:
      fprintf(stderr, "i2d\n");
      break;
    case i2f:
      fprintf(stderr, "i2f\n");
      break;
    case i2l:
      fprintf(stderr, "i2l\n");
      break;
    case i2s:
      fprintf(stderr, "i2s\n");
      break;
    case iadd:
      fprintf(stderr, "iadd\n");
      break;
    case iaload:
      fprintf(stderr, "iaload\n");
      break;
    case iand:
      fprintf(stderr, "iand\n");
      break;
    case iastore:
      fprintf(stderr, "iastore\n");
      break;
    case iconst_m1:
      fprintf(stderr, "iconst_m1\n");
      break;
    case iconst_0:
      fprintf(stderr, "iconst_0\n");
      break;
    case iconst_1:
      fprintf(stderr, "iconst_1\n");
      break;
    case iconst_2:
      fprintf(stderr, "iconst_2\n");
      break;
    case iconst_3:
      fprintf(stderr, "iconst_3\n");
      break;
    case iconst_4:
      fprintf(stderr, "iconst_4\n");
      break;
    case iconst_5:
      fprintf(stderr, "iconst_5\n");
      break;
    case idiv:
      fprintf(stderr, "idiv\n");
      break;

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

    case iload:
      fprintf(stderr, "iload %02x\n", code[ip++]);
      break;
    case fload:
      fprintf(stderr, "fload %02x\n", code[ip++]);
      break;

    case iload_0:
      fprintf(stderr, "iload_0\n");
      break;
    case fload_0:
      fprintf(stderr, "fload_0\n");
      break;
    case iload_1:
      fprintf(stderr, "iload_1\n");
      break;
    case fload_1:
      fprintf(stderr, "fload_1\n");
      break;

    case iload_2:
      fprintf(stderr, "iload_2\n");
      break;
    case fload_2:
      fprintf(stderr, "fload_2\n");
      break;
    case iload_3:
      fprintf(stderr, "iload_3\n");
      break;
    case fload_3:
      fprintf(stderr, "fload_3\n");
      break;

    case imul:
      fprintf(stderr, "imul\n");
      break;
    case ineg:
      fprintf(stderr, "ineg\n");
      break;

    case instanceof:
      fprintf(stderr, "instanceof %04x\n", read16(code, ip));
      break;
    case invokeinterface:
      fprintf(stderr, "invokeinterface %04x\n", read16(code, ip));
      break;
    case invokespecial:
      fprintf(stderr, "invokespecial %04x\n", read16(code, ip));
      break;
    case invokestatic:
      fprintf(stderr, "invokestatic %04x\n", read16(code, ip));
      break;
    case invokevirtual:
      fprintf(stderr, "invokevirtual %04x\n", read16(code, ip));
      break;

    case ior:
      fprintf(stderr, "ior\n");
      break;
    case irem:
      fprintf(stderr, "irem\n");
      break;
    case ireturn:
      fprintf(stderr, "ireturn\n");
      break;
    case freturn:
      fprintf(stderr, "freturn\n");
      break;
    case ishl:
      fprintf(stderr, "ishl\n");
      break;
    case ishr:
      fprintf(stderr, "ishr\n");
      break;

    case istore:
      fprintf(stderr, "istore %02x\n", code[ip++]);
      break;
    case fstore:
      fprintf(stderr, "fstore %02x\n", code[ip++]);
      break;

    case istore_0:
      fprintf(stderr, "istore_0\n");
      break;
    case fstore_0:
      fprintf(stderr, "fstore_0\n");
      break;
    case istore_1:
      fprintf(stderr, "istore_1\n");
      break;
    case fstore_1:
      fprintf(stderr, "fstore_1\n");
      break;
    case istore_2:
      fprintf(stderr, "istore_2\n");
      break;
    case fstore_2:
      fprintf(stderr, "fstore_2\n");
      break;
    case istore_3:
      fprintf(stderr, "istore_3\n");
      break;
    case fstore_3:
      fprintf(stderr, "fstore_3\n");
      break;

    case isub:
      fprintf(stderr, "isub\n");
      break;
    case iushr:
      fprintf(stderr, "iushr\n");
      break;
    case ixor:
      fprintf(stderr, "ixor\n");
      break;

    case jsr:
      fprintf(stderr, "jsr %04x\n", read16(code, ip));
      break;
    case jsr_w:
      fprintf(stderr, "jsr_w %08x\n", read32(code, ip));
      break;

    case l2d:
      fprintf(stderr, "l2d\n");
      break;
    case l2f:
      fprintf(stderr, "l2f\n");
      break;
    case l2i:
      fprintf(stderr, "l2i\n");
      break;
    case ladd:
      fprintf(stderr, "ladd\n");
      break;
    case laload:
      fprintf(stderr, "laload\n");
      break;

    case land:
      fprintf(stderr, "land\n");
      break;
    case lastore:
      fprintf(stderr, "lastore\n");
      break;

    case lcmp:
      fprintf(stderr, "lcmp\n");
      break;
    case lconst_0:
      fprintf(stderr, "lconst_0\n");
      break;
    case lconst_1:
      fprintf(stderr, "lconst_1\n");
      break;

    case ldc:
      fprintf(stderr, "ldc %04x\n", read16(code, ip));
      break;
    case ldc_w:
      fprintf(stderr, "ldc_w %08x\n", read32(code, ip));
      break;
    case ldc2_w:
      fprintf(stderr, "ldc2_w %04x\n", read16(code, ip));
      break;

    case ldiv_:
      fprintf(stderr, "ldiv_\n");
      break;

    case lload:
      fprintf(stderr, "lload %02x\n", code[ip++]);
      break;
    case dload:
      fprintf(stderr, "dload %02x\n", code[ip++]);
      break;

    case lload_0:
      fprintf(stderr, "lload_0\n");
      break;
    case dload_0:
      fprintf(stderr, "dload_0\n");
      break;
    case lload_1:
      fprintf(stderr, "lload_1\n");
      break;
    case dload_1:
      fprintf(stderr, "dload_1\n");
      break;
    case lload_2:
      fprintf(stderr, "lload_2\n");
      break;
    case dload_2:
      fprintf(stderr, "dload_2\n");
      break;
    case lload_3:
      fprintf(stderr, "lload_3\n");
      break;
    case dload_3:
      fprintf(stderr, "dload_3\n");
      break;

    case lmul:
      fprintf(stderr, "lmul\n");
      break;
    case lneg:
      fprintf(stderr, "lneg\n");
      break;

    case lookupswitch: {
      int32_t default_ = read32(code, ip);
      int32_t pairCount = read32(code, ip);
      fprintf(stderr,
              "lookupswitch default: %d pairCount: %d\n",
              default_,
              pairCount);

      for (int i = 0; i < pairCount; i++) {
        int32_t k = read32(code, ip);
        int32_t d = read32(code, ip);
        fprintf(stderr, "%s  key: %02x dest: %2x\n", prefix, k, d);
      }
    } break;

    case lor:
      fprintf(stderr, "lor\n");
      break;
    case lrem:
      fprintf(stderr, "lrem\n");
      break;
    case lreturn:
      fprintf(stderr, "lreturn\n");
      break;
    case dreturn:
      fprintf(stderr, "dreturn\n");
      break;
    case lshl:
      fprintf(stderr, "lshl\n");
      break;
    case lshr:
      fprintf(stderr, "lshr\n");
      break;

    case lstore:
      fprintf(stderr, "lstore %02x\n", code[ip++]);
      break;
    case dstore:
      fprintf(stderr, "dstore %02x\n", code[ip++]);
      break;

    case lstore_0:
      fprintf(stderr, "lstore_0\n");
      break;
    case dstore_0:
      fprintf(stderr, "dstore_0\n");
      break;
    case lstore_1:
      fprintf(stderr, "lstore_1\n");
      break;
    case dstore_1:
      fprintf(stderr, "dstore_1\n");
      break;
    case lstore_2:
      fprintf(stderr, "lstore_2\n");
      break;
    case dstore_2:
      fprintf(stderr, "dstore_2\n");
      break;
    case lstore_3:
      fprintf(stderr, "lstore_3\n");
      break;
    case dstore_3:
      fprintf(stderr, "dstore_3\n");
      break;

    case lsub:
      fprintf(stderr, "lsub\n");
      break;
    case lushr:
      fprintf(stderr, "lushr\n");
      break;
    case lxor:
      fprintf(stderr, "lxor\n");
      break;

    case monitorenter:
      fprintf(stderr, "monitorenter\n");
      break;
    case monitorexit:
      fprintf(stderr, "monitorexit\n");
      break;

    case multianewarray: {
      unsigned type = read16(code, ip);
      fprintf(stderr, "multianewarray %04x %02x\n", type, code[ip++]);
    } break;

    case new_:
      fprintf(stderr, "new %04x\n", read16(code, ip));
      break;

    case newarray:
      fprintf(stderr, "newarray %02x\n", code[ip++]);
      break;

    case nop:
      fprintf(stderr, "nop\n");
      break;
    case pop_:
      fprintf(stderr, "pop\n");
      break;
    case pop2:
      fprintf(stderr, "pop2\n");
      break;

    case putfield:
      fprintf(stderr, "putfield %04x\n", read16(code, ip));
      break;
    case putstatic:
      fprintf(stderr, "putstatic %04x\n", read16(code, ip));
      break;

    case ret:
      fprintf(stderr, "ret %02x\n", code[ip++]);
      break;

    case return_:
      fprintf(stderr, "return_\n");
      break;
    case saload:
      fprintf(stderr, "saload\n");
      break;
    case sastore:
      fprintf(stderr, "sastore\n");
      break;

    case sipush:
      fprintf(stderr, "sipush %04x\n", read16(code, ip));
      break;

    case swap:
      fprintf(stderr, "swap\n");
      break;

    case tableswitch: {
      int32_t default_ = read32(code, ip);
      int32_t bottom = read32(code, ip);
      int32_t top = read32(code, ip);
      fprintf(stderr,
              "tableswitch default: %d bottom: %d top: %d\n",
              default_,
              bottom,
              top);

      for (int i = 0; i < top - bottom + 1; i++) {
        int32_t d = read32(code, ip);
        fprintf(stderr, "%s  key: %d dest: %2x\n", prefix, i + bottom, d);
      }
    } break;

    case wide: {
      switch (code[ip++]) {
      case aload:
        fprintf(stderr, "wide aload %04x\n", read16(code, ip));
        break;

      case astore:
        fprintf(stderr, "wide astore %04x\n", read16(code, ip));
        break;
      case iinc:
        fprintf(stderr,
                "wide iinc %04x %04x\n",
                read16(code, ip),
                read16(code, ip));
        break;
      case iload:
        fprintf(stderr, "wide iload %04x\n", read16(code, ip));
        break;
      case istore:
        fprintf(stderr, "wide istore %04x\n", read16(code, ip));
        break;
      case lload:
        fprintf(stderr, "wide lload %04x\n", read16(code, ip));
        break;
      case lstore:
        fprintf(stderr, "wide lstore %04x\n", read16(code, ip));
        break;
      case ret:
        fprintf(stderr, "wide ret %04x\n", read16(code, ip));
        break;

      default: {
        fprintf(stderr,
                "unknown wide instruction %02x %04x\n",
                instr,
                read16(code, ip));
      }
      }
    } break;

    default: {
      fprintf(stderr, "unknown instruction %02x\n", instr);
    }
    }
  }
}

GcCode* parseCode(Thread* t, Stream& s, GcSingleton* pool)
{
  PROTECT(t, pool);

  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  if (DebugClassReader) {
    fprintf(stderr,
            "    code: maxStack %d maxLocals %d length %d\n",
            maxStack,
            maxLocals,
            length);
  }

  GcCode* code = makeCode(t, pool, 0, 0, 0, 0, 0, maxStack, maxLocals, length);
  s.read(code->body().begin(), length);
  PROTECT(t, code);

  if (DebugClassReader) {
    disassembleCode("      ", code->body().begin(), length);
  }

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    GcExceptionHandlerTable* eht = makeExceptionHandlerTable(t, ehtLength);
    for (unsigned i = 0; i < ehtLength; ++i) {
      unsigned start = s.read2();
      unsigned end = s.read2();
      unsigned ip = s.read2();
      unsigned catchType = s.read2();
      eht->body()[i] = exceptionHandler(start, end, ip, catchType);
    }

    code->setExceptionHandlerTable(t, eht);
  }

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    GcByteArray* name
        = cast<GcByteArray>(t, singletonObject(t, pool, s.read2() - 1));
    unsigned length = s.read4();

    if (vm::strcmp(reinterpret_cast<const int8_t*>("LineNumberTable"),
                   name->body().begin()) == 0) {
      unsigned lntLength = s.read2();
      GcLineNumberTable* lnt = makeLineNumberTable(t, lntLength);
      for (unsigned i = 0; i < lntLength; ++i) {
        unsigned ip = s.read2();
        unsigned line = s.read2();
        lnt->body()[i] = lineNumber(ip, line);
      }

      code->setLineNumberTable(t, lnt);
    } else {
      s.skip(length);
    }
  }

  return code;
}

GcList* addInterfaceMethods(Thread* t,
                            GcClass* class_,
                            GcHashMap* virtualMap,
                            unsigned* virtualCount,
                            bool makeList)
{
  GcArray* itable = cast<GcArray>(t, class_->interfaceTable());
  if (itable) {
    PROTECT(t, class_);
    PROTECT(t, virtualMap);
    PROTECT(t, itable);

    GcList* list = 0;
    PROTECT(t, list);

    GcMethod* method = 0;
    PROTECT(t, method);

    GcArray* vtable = 0;
    PROTECT(t, vtable);

    unsigned stride = (class_->flags() & ACC_INTERFACE) ? 1 : 2;
    for (unsigned i = 0; i < itable->length(); i += stride) {
      vtable = cast<GcArray>(
          t, cast<GcClass>(t, itable->body()[i])->virtualTable());
      if (vtable) {
        for (unsigned j = 0; j < vtable->length(); ++j) {
          method = cast<GcMethod>(t, vtable->body()[j]);
          GcTriple* n
              = hashMapFindNode(t, virtualMap, method, methodHash, methodEqual);
          if (n == 0) {
            method = makeMethod(t,
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
                                method->code());

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

void parseMethodTable(Thread* t, Stream& s, GcClass* class_, GcSingleton* pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  GcHashMap* virtualMap = makeHashMap(t, 0, 0);
  PROTECT(t, virtualMap);

  unsigned virtualCount = 0;
  unsigned declaredVirtualCount = 0;

  GcArray* superVirtualTable = 0;
  PROTECT(t, superVirtualTable);

  if ((class_->flags() & ACC_INTERFACE) == 0) {
    if (class_->super()) {
      superVirtualTable = cast<GcArray>(t, class_->super()->virtualTable());
    }

    if (superVirtualTable) {
      virtualCount = superVirtualTable->length();
      for (unsigned i = 0; i < virtualCount; ++i) {
        object method = superVirtualTable->body()[i];
        hashMapInsert(t, virtualMap, method, method, methodHash);
      }
    }
  }

  GcList* newVirtuals = makeList(t, 0, 0, 0);
  PROTECT(t, newVirtuals);

  unsigned count = s.read2();

  if (DebugClassReader) {
    fprintf(stderr, "  method count %d\n", count);
  }

  if (count) {
    GcArray* methodTable = makeArray(t, count);
    PROTECT(t, methodTable);

    GcMethodAddendum* addendum = 0;
    PROTECT(t, addendum);

    GcCode* code = 0;
    PROTECT(t, code);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      if (DebugClassReader) {
        fprintf(stderr,
                "    method flags %d name %d spec %d '%s%s'\n",
                flags,
                name,
                spec,
                cast<GcByteArray>(t, singletonObject(t, pool, name - 1))
                    ->body()
                    .begin(),
                cast<GcByteArray>(t, singletonObject(t, pool, spec - 1))
                    ->body()
                    .begin());
      }

      addendum = 0;
      code = 0;

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        GcByteArray* attributeName
            = cast<GcByteArray>(t, singletonObject(t, pool, s.read2() - 1));
        unsigned length = s.read4();

        if (vm::strcmp(reinterpret_cast<const int8_t*>("Code"),
                       attributeName->body().begin()) == 0) {
          code = parseCode(t, s, pool);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Exceptions"),
                              attributeName->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0, 0);
          }
          unsigned exceptionCount = s.read2();
          GcShortArray* body = makeShortArray(t, exceptionCount);
          for (unsigned i = 0; i < exceptionCount; ++i) {
            body->body()[i] = s.read2();
          }
          addendum->setExceptionTable(t, body);
        } else if (vm::strcmp(
                       reinterpret_cast<const int8_t*>("AnnotationDefault"),
                       attributeName->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0, 0);
          }

          GcByteArray* body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(body->body().begin()), length);

          addendum->setAnnotationDefault(t, body);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                              attributeName->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0, 0);
          }

          addendum->setSignature(t, singletonObject(t, pool, s.read2() - 1));
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>(
                                  "RuntimeVisibleAnnotations"),
                              attributeName->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0, 0);
          }

          GcByteArray* body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(body->body().begin()), length);

          addendum->setAnnotationTable(t, body);
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>(
                                  "RuntimeVisibleParameterAnnotations"),
                              attributeName->body().begin()) == 0) {
          if (addendum == 0) {
            addendum = makeMethodAddendum(t, pool, 0, 0, 0, 0, 0);
          }

          GcByteArray* body = makeByteArray(t, length);
          s.read(reinterpret_cast<uint8_t*>(body->body().begin()), length);

          addendum->setParameterAnnotationTable(t, body);
        } else {
          s.skip(length);
        }
      }

      const char* specString = reinterpret_cast<const char*>(
          cast<GcByteArray>(t, singletonObject(t, pool, spec - 1))
              ->body()
              .begin());

      unsigned parameterCount;
      unsigned parameterFootprint;
      unsigned returnCode;
      scanMethodSpec(t,
                     specString,
                     flags & ACC_STATIC,
                     &parameterCount,
                     &parameterFootprint,
                     &returnCode);

      GcMethod* method = t->m->processor->makeMethod(
          t,
          0,  // vm flags
          returnCode,
          parameterCount,
          parameterFootprint,
          flags,
          0,  // offset
          cast<GcByteArray>(t, singletonObject(t, pool, name - 1)),
          cast<GcByteArray>(t, singletonObject(t, pool, spec - 1)),
          addendum,
          class_,
          code);

      PROTECT(t, method);

      if (methodVirtual(t, method)) {
        ++declaredVirtualCount;

        GcTriple* p
            = hashMapFindNode(t, virtualMap, method, methodHash, methodEqual);

        if (p) {
          method->offset() = cast<GcMethod>(t, p->first())->offset();

          p->setSecond(t, method);
        } else {
          method->offset() = virtualCount++;

          listAppend(t, newVirtuals, method);

          hashMapInsert(t, virtualMap, method, method, methodHash);
        }

        if (UNLIKELY((class_->flags() & ACC_INTERFACE) == 0
                     and vm::strcmp(reinterpret_cast<const int8_t*>("finalize"),
                                    method->name()->body().begin()) == 0
                     and vm::strcmp(reinterpret_cast<const int8_t*>("()V"),
                                    method->spec()->body().begin()) == 0
                     and (not emptyMethod(t, method)))) {
          class_->vmFlags() |= HasFinalizerFlag;
        }
      } else {
        method->offset() = i;

        if (vm::strcmp(reinterpret_cast<const int8_t*>("<clinit>"),
                       method->name()->body().begin()) == 0) {
          method->vmFlags() |= ClassInitFlag;
          class_->vmFlags() |= NeedInitFlag;
        } else if (vm::strcmp(reinterpret_cast<const int8_t*>("<init>"),
                              method->name()->body().begin()) == 0) {
          method->vmFlags() |= ConstructorFlag;
        }
      }

      methodTable->setBodyElement(t, i, method);
    }

    class_->setMethodTable(t, methodTable);
  }

  GcList* abstractVirtuals
      = addInterfaceMethods(t, class_, virtualMap, &virtualCount, true);

  PROTECT(t, abstractVirtuals);

  bool populateInterfaceVtables = false;

  if (declaredVirtualCount == 0 and abstractVirtuals == 0
      and (class_->flags() & ACC_INTERFACE) == 0) {
    if (class_->super()) {
      // inherit virtual table from superclass
      class_->setVirtualTable(t, superVirtualTable);

      if (class_->super()->interfaceTable()
          and cast<GcArray>(t, class_->interfaceTable())->length()
              == cast<GcArray>(t, class_->super()->interfaceTable())
                     ->length()) {
        // inherit interface table from superclass
        class_->setInterfaceTable(t, class_->super()->interfaceTable());
      } else {
        populateInterfaceVtables = true;
      }
    } else {
      // apparently, Object does not have any virtual methods.  We
      // give it a vtable anyway so code doesn't break elsewhere.
      GcArray* vtable = makeArray(t, 0);
      class_->setVirtualTable(t, vtable);
    }
  } else if (virtualCount) {
    // generate class vtable

    GcArray* vtable = makeArray(t, virtualCount);

    unsigned i = 0;
    if (class_->flags() & ACC_INTERFACE) {
      PROTECT(t, vtable);

      for (HashMapIterator it(t, virtualMap); it.hasMore();) {
        GcMethod* method = cast<GcMethod>(t, it.next()->first());
        assertT(t, vtable->body()[method->offset()] == 0);
        vtable->setBodyElement(t, method->offset(), method);
        ++i;
      }
    } else {
      populateInterfaceVtables = true;

      if (superVirtualTable) {
        for (; i < superVirtualTable->length(); ++i) {
          object method = superVirtualTable->body()[i];
          method = hashMapFind(t, virtualMap, method, methodHash, methodEqual);

          vtable->setBodyElement(t, i, method);
        }
      }

      for (GcPair* p = cast<GcPair>(t, newVirtuals->front()); p;
           p = cast<GcPair>(t, p->second())) {
        vtable->setBodyElement(t, i, p->first());
        ++i;
      }
    }

    if (abstractVirtuals) {
      PROTECT(t, vtable);

      object originalMethodTable = class_->methodTable();
      PROTECT(t, originalMethodTable);

      unsigned oldLength
          = class_->methodTable()
                ? cast<GcArray>(t, class_->methodTable())->length()
                : 0;

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      addendum->declaredMethodCount() = oldLength;

      GcArray* newMethodTable
          = makeArray(t, oldLength + abstractVirtuals->size());

      if (oldLength) {
        GcArray* mtable = cast<GcArray>(t, class_->methodTable());
        for (size_t i = 0; i < oldLength; i++) {
          newMethodTable->setBodyElement(t, i, mtable->body()[i]);
        }
      }

      mark(t, newMethodTable, ArrayBody, oldLength);

      unsigned mti = oldLength;
      for (GcPair* p = cast<GcPair>(t, abstractVirtuals->front()); p;
           p = cast<GcPair>(t, p->second())) {
        newMethodTable->setBodyElement(t, mti++, p->first());

        if ((class_->flags() & ACC_INTERFACE) == 0) {
          vtable->setBodyElement(t, i++, p->first());
        }
      }

      assertT(t, newMethodTable->length() == mti);

      class_->setMethodTable(t, newMethodTable);
    }

    assertT(t, vtable->length() == i);

    class_->setVirtualTable(t, vtable);
  }

  if (populateInterfaceVtables) {
    // generate interface vtables
    GcArray* itable = cast<GcArray>(t, class_->interfaceTable());
    if (itable) {
      PROTECT(t, itable);

      for (unsigned i = 0; i < itable->length(); i += 2) {
        GcArray* ivtable = cast<GcArray>(
            t, cast<GcClass>(t, itable->body()[i])->virtualTable());
        if (ivtable) {
          GcArray* vtable = cast<GcArray>(t, itable->body()[i + 1]);

          for (unsigned j = 0; j < ivtable->length(); ++j) {
            object method = ivtable->body()[j];
            method
                = hashMapFind(t, virtualMap, method, methodHash, methodEqual);
            assertT(t, method);

            vtable->setBodyElement(t, j, method);
          }
        }
      }
    }
  }
}

void parseAttributeTable(Thread* t,
                         Stream& s,
                         GcClass* class_,
                         GcSingleton* pool)
{
  PROTECT(t, class_);
  PROTECT(t, pool);

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    GcByteArray* name
        = cast<GcByteArray>(t, singletonObject(t, pool, s.read2() - 1));
    unsigned length = s.read4();

    if (vm::strcmp(reinterpret_cast<const int8_t*>("SourceFile"),
                   name->body().begin()) == 0) {
      class_->setSourceFile(
          t, cast<GcByteArray>(t, singletonObject(t, pool, s.read2() - 1)));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("Signature"),
                          name->body().begin()) == 0) {
      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      addendum->setSignature(t, singletonObject(t, pool, s.read2() - 1));
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("InnerClasses"),
                          name->body().begin()) == 0) {
      unsigned innerClassCount = s.read2();
      GcArray* table = makeArray(t, innerClassCount);
      PROTECT(t, table);

      for (unsigned i = 0; i < innerClassCount; ++i) {
        int16_t inner = s.read2();
        int16_t outer = s.read2();
        int16_t name = s.read2();
        int16_t flags = s.read2();

        GcInnerClassReference* reference = makeInnerClassReference(
            t,
            inner
                ? cast<GcReference>(t, singletonObject(t, pool, inner - 1))
                      ->name()
                : 0,
            outer
                ? cast<GcReference>(t, singletonObject(t, pool, outer - 1))
                      ->name()
                : 0,
            name ? cast<GcByteArray>(t, singletonObject(t, pool, name - 1)) : 0,
            flags);

        table->setBodyElement(t, i, reference);
      }

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      addendum->setInnerClassTable(t, table);
    } else if (vm::strcmp(
                   reinterpret_cast<const int8_t*>("RuntimeVisibleAnnotations"),
                   name->body().begin()) == 0) {
      GcByteArray* body = makeByteArray(t, length);
      PROTECT(t, body);
      s.read(reinterpret_cast<uint8_t*>(body->body().begin()), length);

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      addendum->setAnnotationTable(t, body);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("BootstrapMethods"),
                          name->body().begin()) == 0) {
      unsigned count = s.read2();
      GcArray* array = makeArray(t, count);
      PROTECT(t, array);

      for (unsigned i = 0; i < count; ++i) {
        unsigned reference = s.read2() - 1;
        unsigned argumentCount = s.read2();
        GcCharArray* element = makeCharArray(t, 1 + argumentCount);
        element->body()[0] = reference;
        for (unsigned ai = 0; ai < argumentCount; ++ai) {
          element->body()[1 + ai] = s.read2() - 1;
        }
        array->setBodyElement(t, i, element);
      }

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);
      addendum->setBootstrapMethodTable(t, array);
    } else if (vm::strcmp(reinterpret_cast<const int8_t*>("EnclosingMethod"),
                          name->body().begin()) == 0) {
      int16_t enclosingClass = s.read2();
      int16_t enclosingMethod = s.read2();

      GcClassAddendum* addendum = getClassAddendum(t, class_, pool);

      addendum->setEnclosingClass(
          t,
          cast<GcReference>(t, singletonObject(t, pool, enclosingClass - 1))
              ->name());

      addendum->setEnclosingMethod(
          t,
          enclosingMethod
              ? cast<GcPair>(t, singletonObject(t, pool, enclosingMethod - 1))
              : 0);
    } else {
      s.skip(length);
    }
  }
}

void updateClassTables(Thread* t, GcClass* newClass, GcClass* oldClass)
{
  GcArray* fieldTable = cast<GcArray>(t, newClass->fieldTable());
  if (fieldTable) {
    for (unsigned i = 0; i < fieldTable->length(); ++i) {
      cast<GcField>(t, fieldTable->body()[i])->setClass(t, newClass);
    }
  }

  GcSingleton* staticTable = newClass->staticTable();
  if (staticTable) {
    staticTable->setBodyElement(t, 0, reinterpret_cast<uintptr_t>(newClass));
  }

  if (newClass->flags() & ACC_INTERFACE) {
    GcArray* virtualTable = cast<GcArray>(t, newClass->virtualTable());
    if (virtualTable) {
      for (unsigned i = 0; i < virtualTable->length(); ++i) {
        GcMethod* m = cast<GcMethod>(t, virtualTable->body()[i]);
        if (m->class_() == oldClass) {
          m->setClass(t, newClass);
        }
      }
    }
  }

  GcArray* methodTable = cast<GcArray>(t, newClass->methodTable());
  if (methodTable) {
    for (unsigned i = 0; i < methodTable->length(); ++i) {
      cast<GcMethod>(t, methodTable->body()[i])->setClass(t, newClass);
    }
  }
}

void updateBootstrapClass(Thread* t, GcClass* bootstrapClass, GcClass* class_)
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

  bootstrapClass->setArrayElementClass(t, class_->arrayElementClass());
  bootstrapClass->setSuper(t, class_->super());
  bootstrapClass->setInterfaceTable(t, class_->interfaceTable());
  bootstrapClass->setVirtualTable(t, class_->virtualTable());
  bootstrapClass->setFieldTable(t, class_->fieldTable());
  bootstrapClass->setMethodTable(t, class_->methodTable());
  bootstrapClass->setStaticTable(t, class_->staticTable());
  bootstrapClass->setAddendum(t, class_->addendum());

  updateClassTables(t, bootstrapClass, class_);
}

GcClass* makeArrayClass(Thread* t,
                        GcClassLoader* loader,
                        unsigned dimensions,
                        GcByteArray* spec,
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
    resolveSystemClass(
        t, roots(t)->bootLoader(), type(t, GcJobject::Type)->name(), false);
  }

  GcArray* vtable = cast<GcArray>(t, type(t, GcJobject::Type)->virtualTable());

  // From JDK docs: for array classes the public, private, protected modifiers are the same as
  // the underlying type, and the final modifier is always set. Testing on OpenJDK shows that
  // ACC_ABSTRACT is also set on array classes.
  int flags = elementClass->flags() & (ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED);
  flags |= ACC_FINAL;
  flags |= ACC_ABSTRACT;

  GcClass* c = t->m->processor->makeClass(t,
                                          flags,
                                          0,
                                          2 * BytesPerWord,
                                          BytesPerWord,
                                          dimensions,
                                          elementClass,
                                          type(t, GcArray::Type)->objectMask(),
                                          spec,
                                          0,
                                          type(t, GcJobject::Type),
                                          roots(t)->arrayInterfaceTable(),
                                          vtable,
                                          0,
                                          0,
                                          0,
                                          0,
                                          loader,
                                          vtable->length());

  PROTECT(t, c);

  t->m->processor->initVtable(t, c);

  return c;
}

void saveLoadedClass(Thread* t, GcClassLoader* loader, GcClass* c)
{
  PROTECT(t, loader);
  PROTECT(t, c);

  ACQUIRE(t, t->m->classLock);

  if (loader->map() == 0) {
    GcHashMap* map = makeHashMap(t, 0, 0);
    loader->setMap(t, map);
  }

  hashMapInsert(
      t, cast<GcHashMap>(t, loader->map()), c->name(), c, byteArrayHash);
}

GcClass* makeArrayClass(Thread* t,
                        GcClassLoader* loader,
                        GcByteArray* spec,
                        bool throw_,
                        Gc::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  const char* s = reinterpret_cast<const char*>(spec->body().begin());
  const char* start = s;
  unsigned dimensions = 0;
  for (; *s == '['; ++s)
    ++dimensions;

  GcByteArray* elementSpec;
  switch (*s) {
  case 'L': {
    ++s;
    const char* elementSpecStart = s;
    while (*s and *s != ';')
      ++s;
    if (dimensions > 1) {
      elementSpecStart -= dimensions;
      ++s;
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
      --dimensions;
    } else {
      abort(t);
    }
  }

  GcClass* elementClass
      = cast<GcClass>(t,
                      hashMapFind(t,
                                  roots(t)->bootstrapClassMap(),
                                  elementSpec,
                                  byteArrayHash,
                                  byteArrayEqual));

  if (elementClass == 0) {
    elementClass = resolveClass(t, loader, elementSpec, throw_, throwType);
    if (elementClass == 0)
      return 0;
  }

  PROTECT(t, elementClass);

  ACQUIRE(t, t->m->classLock);

  GcClass* class_ = findLoadedClass(t, elementClass->loader(), spec);
  if (class_) {
    return class_;
  }

  class_ = makeArrayClass(
      t, elementClass->loader(), dimensions, spec, elementClass);

  PROTECT(t, class_);

  saveLoadedClass(t, elementClass->loader(), class_);

  return class_;
}

GcClass* resolveArrayClass(Thread* t,
                           GcClassLoader* loader,
                           GcByteArray* spec,
                           bool throw_,
                           Gc::Type throwType)
{
  GcClass* c = cast<GcClass>(t,
                             hashMapFind(t,
                                         roots(t)->bootstrapClassMap(),
                                         spec,
                                         byteArrayHash,
                                         byteArrayEqual));

  if (c) {
    c->setVirtualTable(t, type(t, GcJobject::Type)->virtualTable());

    return c;
  } else {
    PROTECT(t, loader);
    PROTECT(t, spec);

    c = findLoadedClass(t, roots(t)->bootLoader(), spec);

    if (c) {
      return c;
    } else {
      return makeArrayClass(t, loader, spec, throw_, throwType);
    }
  }
}

void removeMonitor(Thread* t, object o)
{
  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  object m
      = hashMapRemove(t, roots(t)->monitorMap(), o, objectHash, objectEqual);

  if (DebugMonitors) {
    fprintf(stderr, "dispose monitor %p for object %x\n", m, hash);
  }
}

void removeString(Thread* t, object o)
{
  hashMapRemove(t, roots(t)->stringMap(), o, stringHash, objectEqual);
}

void bootClass(Thread* t,
               Gc::Type type,
               int superType,
               uint32_t* objectMask,
               unsigned fixedSize,
               unsigned arrayElementSize,
               unsigned vtableLength)
{
  GcClass* super
      = (superType >= 0 ? vm::type(t, static_cast<Gc::Type>(superType)) : 0);

  unsigned maskSize
      = ceilingDivide(fixedSize + arrayElementSize, 32 * BytesPerWord);

  GcIntArray* mask;
  if (objectMask) {
    if (super and super->objectMask()
        and super->objectMask()->length() == maskSize
        and memcmp(super->objectMask()->body().begin(),
                   objectMask,
                   sizeof(uint32_t) * maskSize) == 0) {
      mask = vm::type(t, static_cast<Gc::Type>(superType))->objectMask();
    } else {
      mask = makeIntArray(t, maskSize);
      memcpy(mask->body().begin(), objectMask, sizeof(uint32_t) * maskSize);
    }
  } else {
    mask = 0;
  }

  int flags = 0;
  switch(type) {
    case Gc::JbyteType:
    case Gc::JintType:
    case Gc::JshortType:
    case Gc::JlongType:
    case Gc::JbooleanType:
    case Gc::JcharType:
    case Gc::JfloatType:
    case Gc::JdoubleType:

    case Gc::ByteArrayType:
    case Gc::IntArrayType:
    case Gc::ShortArrayType:
    case Gc::LongArrayType:
    case Gc::BooleanArrayType:
    case Gc::CharArrayType:
    case Gc::FloatArrayType:
    case Gc::DoubleArrayType:
      // Primitive and array types are final, abstract and public.
      flags = ACC_FINAL | ACC_ABSTRACT | ACC_PUBLIC;
    default:
      break;
  }

  super = (superType >= 0 ? vm::type(t, static_cast<Gc::Type>(superType)) : 0);

  GcClass* class_ = t->m->processor->makeClass(t,
                                               flags,
                                               BootstrapFlag,
                                               fixedSize,
                                               arrayElementSize,
                                               arrayElementSize ? 1 : 0,
                                               0,
                                               mask,
                                               0,
                                               0,
                                               super,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0,
                                               0,
                                               roots(t)->bootLoader(),
                                               vtableLength);

  setType(t, type, class_);
}

void bootJavaClass(Thread* t,
                   Gc::Type type,
                   int superType,
                   const char* name,
                   int vtableLength,
                   object bootMethod)
{
  PROTECT(t, bootMethod);

  GcByteArray* n = makeByteArray(t, name);
  PROTECT(t, n);

  GcClass* class_ = vm::type(t, type);
  PROTECT(t, class_);

  class_->setName(t, n);

  GcArray* vtable;
  if (vtableLength >= 0) {
    vtable = makeArray(t, vtableLength);
    for (int i = 0; i < vtableLength; ++i) {
      vtable->setBodyElement(t, i, bootMethod);
    }
  } else {
    vtable = cast<GcArray>(
        t, vm::type(t, static_cast<Gc::Type>(superType))->virtualTable());
  }

  class_->setVirtualTable(t, vtable);

  t->m->processor->initVtable(t, class_);

  hashMapInsert(t, roots(t)->bootstrapClassMap(), n, class_, byteArrayHash);
}

void nameClass(Thread* t, Gc::Type type, const char* name)
{
  GcByteArray* n = makeByteArray(t, name);
  cast<GcClass>(t, t->m->types->body()[type])->setName(t, n);
}

void makeArrayInterfaceTable(Thread* t)
{
  GcArray* interfaceTable = makeArray(t, 4);

  interfaceTable->setBodyElement(t, 0, type(t, GcSerializable::Type));

  interfaceTable->setBodyElement(t, 2, type(t, GcCloneable::Type));

  roots(t)->setArrayInterfaceTable(t, interfaceTable);
}

void boot(Thread* t)
{
  Machine* m = t->m;

  m->unsafe = true;

  m->roots = reinterpret_cast<GcRoots*>(allocate(t, GcRoots::FixedSize, true));

  object classLoader = allocate(t, GcSystemClassLoader::FixedSize, true);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setBootLoader(t, reinterpret_cast<GcClassLoader*>(classLoader));

  classLoader = allocate(t, GcSystemClassLoader::FixedSize, true);
  // sequence point, for gc (don't recombine statements)
  roots(t)->setAppLoader(t, reinterpret_cast<GcClassLoader*>(classLoader));

  m->types = reinterpret_cast<GcArray*>(
      allocate(t, pad((TypeCount + 2) * BytesPerWord), true));
  m->types->length() = TypeCount;

#include "type-initializations.cpp"

  GcClass* arrayClass = type(t, GcArray::Type);
  setField(t, m->types, 0, arrayClass);

  GcClass* rootsClass = type(t, GcRoots::Type);
  setField(t, m->roots, 0, rootsClass);

  GcClass* loaderClass = type(t, GcSystemClassLoader::Type);
  setField(t, roots(t)->bootLoader(), 0, loaderClass);
  setField(t, roots(t)->appLoader(), 0, loaderClass);

  GcClass* objectClass = type(t, GcJobject::Type);

  GcClass* classClass = type(t, GcClass::Type);
  setField(t, classClass, 0, classClass);
  classClass->setSuper(t, objectClass);

  GcClass* intArrayClass = type(t, GcIntArray::Type);
  setField(t, intArrayClass, 0, classClass);
  intArrayClass->setSuper(t, objectClass);

  m->unsafe = false;

  type(t, GcSingleton::Type)->vmFlags() |= SingletonFlag;

  type(t, GcContinuation::Type)->vmFlags() |= ContinuationFlag;

  type(t, GcJreference::Type)->vmFlags() |= ReferenceFlag;
  type(t, GcWeakReference::Type)->vmFlags() |= ReferenceFlag
                                               | WeakReferenceFlag;
  type(t, GcSoftReference::Type)->vmFlags() |= ReferenceFlag
                                               | WeakReferenceFlag;
  type(t, GcPhantomReference::Type)->vmFlags() |= ReferenceFlag
                                                  | WeakReferenceFlag;

  type(t, GcJboolean::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJbyte::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJchar::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJshort::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJint::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJlong::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJfloat::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJdouble::Type)->vmFlags() |= PrimitiveFlag;
  type(t, GcJvoid::Type)->vmFlags() |= PrimitiveFlag;

  type(t, GcBooleanArray::Type)
      ->setArrayElementClass(t, type(t, GcJboolean::Type));
  type(t, GcByteArray::Type)->setArrayElementClass(t, type(t, GcJbyte::Type));
  type(t, GcCharArray::Type)->setArrayElementClass(t, type(t, GcJchar::Type));
  type(t, GcShortArray::Type)->setArrayElementClass(t, type(t, GcJshort::Type));
  type(t, GcIntArray::Type)->setArrayElementClass(t, type(t, GcJint::Type));
  type(t, GcLongArray::Type)->setArrayElementClass(t, type(t, GcJlong::Type));
  type(t, GcFloatArray::Type)->setArrayElementClass(t, type(t, GcJfloat::Type));
  type(t, GcDoubleArray::Type)
      ->setArrayElementClass(t, type(t, GcJdouble::Type));

  {
    GcHashMap* map = makeHashMap(t, 0, 0);
    roots(t)->bootLoader()->setMap(t, map);
  }

  roots(t)->bootLoader()->as<GcSystemClassLoader>(t)->finder() = m->bootFinder;

  {
    GcHashMap* map = makeHashMap(t, 0, 0);
    roots(t)->appLoader()->setMap(t, map);
  }

  roots(t)->appLoader()->as<GcSystemClassLoader>(t)->finder() = m->appFinder;

  roots(t)->appLoader()->setParent(t, roots(t)->bootLoader());

  {
    GcHashMap* map = makeHashMap(t, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(t)->setBootstrapClassMap(t, map);
  }

  {
    GcWeakHashMap* map = makeWeakHashMap(t, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(t)->setStringMap(t, map->as<GcHashMap>(t));
  }

  makeArrayInterfaceTable(t);

  type(t, GcBooleanArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcByteArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcCharArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcShortArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcIntArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcLongArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcFloatArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());
  type(t, GcDoubleArray::Type)
      ->setInterfaceTable(t, roots(t)->arrayInterfaceTable());

  m->processor->boot(t, 0, 0);

  {
    GcCode* bootCode = makeCode(t, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    bootCode->body()[0] = impdep1;
    object bootMethod
        = makeMethod(t, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, bootCode);
    PROTECT(t, bootMethod);

#    include "type-java-initializations.cpp"
#    include "type-name-initializations.cpp"

  }

}

class HeapClient : public Heap::Client {
 public:
  HeapClient(Machine* m) : m(m)
  {
  }

  virtual void visitRoots(Heap::Visitor* v)
  {
    ::visitRoots(m, v);

    postVisit(m->rootThread, v);
  }

  virtual void collect(void* context, Heap::CollectionType type)
  {
    collect(static_cast<Thread*>(context), type);
  }

  virtual bool isFixed(void* p)
  {
    return objectFixed(m->rootThread, static_cast<object>(p));
  }

  virtual unsigned sizeInWords(void* p)
  {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));

    unsigned n = baseSize(t, o, m->heap->follow(objectClass(t, o)));

    if (objectExtended(t, o)) {
      ++n;
    }

    return n;
  }

  virtual unsigned copiedSizeInWords(void* p)
  {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));
    assertT(t, not objectFixed(t, o));

    unsigned n = baseSize(t, o, m->heap->follow(objectClass(t, o)));

    if (objectExtended(t, o) or hashTaken(t, o)) {
      ++n;
    }

    return n;
  }

  virtual void copy(void* srcp, void* dstp)
  {
    Thread* t = m->rootThread;

    object src = static_cast<object>(m->heap->follow(maskAlignedPointer(srcp)));
    assertT(t, not objectFixed(t, src));

    GcClass* class_ = m->heap->follow(objectClass(t, src));

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

  virtual void walk(void* p, Heap::Walker* w)
  {
    object o = static_cast<object>(m->heap->follow(maskAlignedPointer(p)));
    ::walk(m->rootThread, w, o, 0);
  }

  void dispose()
  {
    m->heap->free(this, sizeof(*this));
  }

 private:
  Machine* m;
};

void doCollect(Thread* t, Heap::CollectionType type, int pendingAllocation)
{
  expect(t, not t->m->collecting);

  t->m->collecting = true;
  THREAD_RESOURCE0(t, t->m->collecting = false);

#ifdef VM_STRESS
  bool stress = (t->getFlags() & Thread::StressFlag) != 0;
  if (not stress)
    t->setFlag(Thread::StressFlag);
#endif

  Machine* m = t->m;

  m->unsafe = true;
  m->heap->collect(
      type,
      footprint(m->rootThread),
      pendingAllocation - (t->m->heapPoolIndex * ThreadHeapSizeInWords));
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
  if (not stress)
    t->clearFlag(Thread::StressFlag);
#endif

  GcFinalizer* finalizeQueue = t->m->finalizeQueue;
  t->m->finalizeQueue = 0;
  for (; finalizeQueue;
       finalizeQueue = cast<GcFinalizer>(t, finalizeQueue->next())) {
    void (*function)(Thread*, object);
    memcpy(&function, &finalizeQueue->finalize(), BytesPerWord);
    function(t, finalizeQueue->target());
  }

#ifndef SGX
  if ((roots(t)->objectsToFinalize() or roots(t)->objectsToClean())
      and m->finalizeThread == 0 and t->state != Thread::ExitState) {
    m->finalizeThread = m->processor->makeThread(
        m, roots(t)->finalizerThread(), m->rootThread);

    addThread(t, m->finalizeThread);

    if (not startThread(t, m->finalizeThread)) {
      removeThread(t, m->finalizeThread);
      m->finalizeThread = 0;
    }
  }
#endif
}

uint64_t invokeLoadClass(Thread* t, uintptr_t* arguments)
{
  GcMethod* method = cast<GcMethod>(t, reinterpret_cast<object>(arguments[0]));
  object loader = reinterpret_cast<object>(arguments[1]);
  object specString = reinterpret_cast<object>(arguments[2]);

  return reinterpret_cast<uintptr_t>(
      t->m->processor->invoke(t, method, loader, specString));
}

bool isInitializing(Thread* t, GcClass* c)
{
  for (Thread::ClassInitStack* s = t->classInitStack; s; s = s->next) {
    if (s->class_ == c) {
      return true;
    }
  }
  return false;
}

object findInTable(Thread* t,
                   GcArray* table,
                   GcByteArray* name,
                   GcByteArray* spec,
                   GcByteArray* (*getName)(Thread*, object),
                   GcByteArray* (*getSpec)(Thread*, object))
{
  if (table) {
    for (unsigned i = 0; i < table->length(); ++i) {
      object o = table->body()[i];
      if (vm::strcmp(getName(t, o)->body().begin(), name->body().begin()) == 0
          and vm::strcmp(getSpec(t, o)->body().begin(), spec->body().begin())
              == 0) {
        return o;
      }
    }

    if (false) {
      fprintf(
          stderr, "%s %s not in\n", name->body().begin(), spec->body().begin());

      for (unsigned i = 0; i < table->length(); ++i) {
        object o = table->body()[i];
        fprintf(stderr,
                "\t%s %s\n",
                getName(t, o)->body().begin(),
                getSpec(t, o)->body().begin());
      }
    }
  }

  return 0;
}

void updatePackageMap(Thread* t, GcClass* class_)
{
  PROTECT(t, class_);

  if (roots(t)->packageMap() == 0) {
    GcHashMap* map = makeHashMap(t, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(t)->setPackageMap(t, map);
  }

  GcByteArray* className = class_->name();
  if ('[' != className->body()[0]) {
    THREAD_RUNTIME_ARRAY(t, char, packageName, className->length());

    char* s = reinterpret_cast<char*>(className->body().begin());
    char* p = strrchr(s, '/');

    if (p) {
      int length = (p - s) + 1;
      memcpy(
          RUNTIME_ARRAY_BODY(packageName), className->body().begin(), length);
      RUNTIME_ARRAY_BODY(packageName)[length] = 0;

      GcByteArray* key
          = vm::makeByteArray(t, "%s", RUNTIME_ARRAY_BODY(packageName));
      PROTECT(t, key);

      hashMapRemove(
          t, roots(t)->packageMap(), key, byteArrayHash, byteArrayEqual);

      GcByteArray* source = class_->source();
      if (source) {
        // note that we strip the "file:" prefix, since OpenJDK's
        // Package.defineSystemPackage expects an unadorned filename:
        const unsigned PrefixLength = 5;
        unsigned sourceNameLength = source->length() - PrefixLength;
        THREAD_RUNTIME_ARRAY(t, char, sourceName, sourceNameLength);
        memcpy(RUNTIME_ARRAY_BODY(sourceName),
               &source->body()[PrefixLength],
               sourceNameLength);

        source = vm::makeByteArray(t, "%s", RUNTIME_ARRAY_BODY(sourceName));
      } else {
        source = vm::makeByteArray(t, "avian-dummy-package-source");
      }

      hashMapInsert(t, roots(t)->packageMap(), key, source, byteArrayHash);
    }
  }
}

}  // namespace

namespace vm {

Machine::Machine(System* system,
                 Heap* heap,
                 Finder* bootFinder,
                 Finder* appFinder,
                 Processor* processor,
                 Classpath* classpath,
                 const char** properties,
                 unsigned propertyCount,
                 const char** arguments,
                 unsigned argumentCount,
                 unsigned stackSizeInBytes)
    : vtable(&javaVMVTable),
      system(system),
      heapClient(new (heap->allocate(sizeof(HeapClient))) HeapClient(this)),
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
  for (unsigned int i = 0; i < propertyCount; i++) {
    size_t length = strlen(properties[i]) + 1;  // +1 for null-terminating char
    this->properties[i] = (char*)heap->allocate(sizeof(char) * length);
    memcpy(this->properties[i], properties[i], length);
  }

  const char* bootstrapProperty = findProperty(this, BOOTSTRAP_PROPERTY);
  const char* bootstrapPropertyDup
      = bootstrapProperty ? strdup(bootstrapProperty) : 0;
  const char* bootstrapPropertyEnd
      = bootstrapPropertyDup
        + (bootstrapPropertyDup ? strlen(bootstrapPropertyDup) : 0);
  char* codeLibraryName = (char*)bootstrapPropertyDup;
  char* codeLibraryNameEnd = 0;
  if (codeLibraryName && (codeLibraryNameEnd
                          = strchr(codeLibraryName, system->pathSeparator())))
    *codeLibraryNameEnd = 0;

  if (not system->success(system->make(&localThread))
      or not system->success(system->make(&stateLock))
      or not system->success(system->make(&heapLock))
      or not system->success(system->make(&classLock))
      or not system->success(system->make(&referenceLock))
      or not system->success(system->make(&shutdownLock))
      or not system->success(system->load(&libraries, bootstrapPropertyDup))) {
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

  if (bootstrapPropertyDup)
    free((void*)bootstrapPropertyDup);
}

void Machine::dispose()
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

  for (unsigned int i = 0; i < propertyCount; i++) {
    heap->free(properties[i], sizeof(char) * (strlen(properties[i]) + 1));
  }
  heap->free(properties, sizeof(const char*) * propertyCount);

  static_cast<HeapClient*>(heapClient)->dispose();

  heap->free(this, sizeof(*this));
}

Thread::Thread(Machine* m, GcThread* javaThread, Thread* parent)
    : vtable(&(m->jniEnvVTable)),
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
      defaultHeap(
          static_cast<uintptr_t*>(m->heap->allocate(ThreadHeapSizeInBytes))),
      heap(defaultHeap),
      backupHeapIndex(0),
      flags(ActiveFlag)
{
}

void Thread::init()
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
      const char* symbolName = lzma ? imageFunctionName + 5 : imageFunctionName;

      void* imagep = m->libraries->resolve(symbolName);
      if (imagep) {
        uint8_t* (*imageFunction)(size_t*);
        memcpy(&imageFunction, &imagep, BytesPerWord);

        size_t size = 0;
        uint8_t* imageBytes = imageFunction(&size);
        if (lzma) {
#ifdef AVIAN_USE_LZMA
          m->bootimage = image = reinterpret_cast<BootImage*>(decodeLZMA(
              m->system, m->heap, imageBytes, size, &(m->bootimageSize)));
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
            uint8_t* (*codeFunction)(size_t*);
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

    GcWeakHashMap* map = makeWeakHashMap(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setByteArrayMap(this, map->as<GcHashMap>(this));

    map = makeWeakHashMap(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setMonitorMap(this, map->as<GcHashMap>(this));

    GcVector* v = makeVector(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setClassRuntimeDataTable(this, v);

    v = makeVector(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setMethodRuntimeDataTable(this, v);

    v = makeVector(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setJNIMethodTable(this, v);

    v = makeVector(this, 0, 0);
    // sequence point, for gc (don't recombine statements)
    roots(this)->setJNIFieldTable(this, v);

    m->localThread->set(this);
  }

  expect(this, m->system->success(m->system->make(&lock)));
}

void Thread::exit()
{
  if (state != Thread::ExitState and state != Thread::ZombieState) {
    enter(this, Thread::ExclusiveState);

    if (m->liveCount == 1) {
      turnOffTheLights(this);
    } else {
      javaThread->peer() = 0;

      enter(this, Thread::ZombieState);
    }
  }
}

void Thread::dispose()
{
  if (lock) {
    lock->dispose();
  }

  if (systemThread) {
    systemThread->dispose();
  }

  --m->threadCount;

  m->heap->free(defaultHeap, ThreadHeapSizeInBytes);

  m->processor->dispose(this);
}

bool Thread::isBlacklisting()
{
  return (javaThread != NULL) && javaThread->blacklisting();
}

void shutDown(Thread* t)
{
  ACQUIRE(t, t->m->shutdownLock);

  GcPair* hooks = roots(t)->shutdownHooks();
  PROTECT(t, hooks);

  roots(t)->setShutdownHooks(t, 0);

  GcPair* h = hooks;
  PROTECT(t, h);
  for (; h; h = cast<GcPair>(t, h->second())) {
    startThread(t, cast<GcThread>(t, h->first()));
  }

  // wait for hooks to exit
  h = hooks;
  for (; h; h = cast<GcPair>(t, h->second())) {
    while (true) {
      Thread* ht
          = reinterpret_cast<Thread*>(cast<GcThread>(t, h->first())->peer());

      {
        ACQUIRE(t, t->m->stateLock);

        if (ht == 0 or ht->state == Thread::ZombieState
            or ht->state == Thread::JoinedState) {
          break;
        } else {
          ENTER(t, Thread::IdleState);
          t->m->stateLock->wait(t->systemThread, 0);
        }
      }
    }
  }

  // tell finalize thread to exit and wait for it to do so
  {
    ACQUIRE(t, t->m->stateLock);
    Thread* finalizeThread = t->m->finalizeThread;
    if (finalizeThread) {
      t->m->finalizeThread = 0;
      t->m->stateLock->notifyAll(t->systemThread);

      while (finalizeThread->state != Thread::ZombieState
             and finalizeThread->state != Thread::JoinedState) {
        ENTER(t, Thread::IdleState);
        t->m->stateLock->wait(t->systemThread, 0);
      }
    }
  }

  // interrupt daemon threads and tell them to die

  // todo: be more aggressive about killing daemon threads, e.g. at
  // any GC point, not just at waits/sleeps
  {
    ACQUIRE(t, t->m->stateLock);

    t->m->alive = false;

    visitAll(t, t->m->rootThread, interruptDaemon);
  }
}

void enter(Thread* t, Thread::State s)
{
  stress(t);

  if (s == t->state)
    return;

  if (t->state == Thread::ExitState) {
    // once in exit state, we stay that way
    return;
  }

#ifdef USE_ATOMIC_OPERATIONS
#define INCREMENT atomicIncrement
#define ACQUIRE_LOCK ACQUIRE_RAW(t, t->m->stateLock)
#define STORE_LOAD_MEMORY_BARRIER storeLoadMemoryBarrier()
#else
#define INCREMENT(pointer, value) *(pointer) += value;
#define ACQUIRE_LOCK
#define STORE_LOAD_MEMORY_BARRIER

  ACQUIRE_RAW(t, t->m->stateLock);
#endif  // not USE_ATOMIC_OPERATIONS

  switch (s) {
  case Thread::ExclusiveState: {
    ACQUIRE_LOCK;

    while (t->m->exclusive) {
      // another thread got here first.
      ENTER(t, Thread::IdleState);
      t->m->stateLock->wait(t->systemThread, 0);
    }

    switch (t->state) {
    case Thread::ActiveState:
      break;

    case Thread::IdleState: {
      INCREMENT(&(t->m->activeCount), 1);
    } break;

    default:
      abort(t);
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

      STORE_LOAD_MEMORY_BARRIER;

      if (t->m->exclusive) {
        ACQUIRE_LOCK;

        t->m->stateLock->notifyAll(t->systemThread);
      }

      break;
    } else {
      // fall through to slow path
    }
    /* fallthrough */

  case Thread::ZombieState: {
    ACQUIRE_LOCK;

    switch (t->state) {
    case Thread::ExclusiveState: {
      assertT(t, t->m->exclusive == t);
      t->m->exclusive = 0;
    } break;

    case Thread::ActiveState:
      break;

    default:
      abort(t);
    }

    assertT(t, t->m->activeCount > 0);
    INCREMENT(&(t->m->activeCount), -1);

    if (s == Thread::ZombieState) {
      assertT(t, t->m->liveCount > 0);
      --t->m->liveCount;

      if (t->getFlags() & Thread::DaemonFlag) {
        --t->m->daemonCount;
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

      STORE_LOAD_MEMORY_BARRIER;

      if (t->m->exclusive) {
        // another thread has entered the exclusive state, so we
        // return to idle and use the slow path to become active
        enter(t, Thread::IdleState);
      } else {
        break;
      }
    }

    {
      ACQUIRE_LOCK;

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
          ++t->m->liveCount;
          ++t->m->threadCount;
        }
        t->state = s;
      } break;

      default:
        abort(t);
      }
    }
    break;

  case Thread::ExitState: {
    ACQUIRE_LOCK;

    switch (t->state) {
    case Thread::ExclusiveState: {
      assertT(t, t->m->exclusive == t);
      // exit state should also be exclusive, so don't set exclusive = 0

      t->m->stateLock->notifyAll(t->systemThread);
    } break;

    case Thread::ActiveState:
      break;

    default:
      abort(t);
    }

    assertT(t, t->m->activeCount > 0);
    INCREMENT(&(t->m->activeCount), -1);

    t->state = s;

    while (t->m->liveCount - t->m->daemonCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  } break;

  default:
    abort(t);
  }
}

object allocate2(Thread* t, unsigned sizeInBytes, bool objectMask)
{
  return allocate3(
      t,
      t->m->heap,
      ceilingDivide(sizeInBytes, BytesPerWord) > ThreadHeapSizeInWords
          ? Machine::FixedAllocation
          : Machine::MovableAllocation,
      sizeInBytes,
      objectMask);
}

object allocate3(Thread* t,
                 Alloc* allocator,
                 Machine::AllocationType type,
                 unsigned sizeInBytes,
                 bool objectMask)
{
  expect(t, t->criticalLevel == 0);

  if (UNLIKELY(t->getFlags() & Thread::UseBackupHeapFlag)) {
    expect(t,
           t->backupHeapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
           <= ThreadBackupHeapSizeInWords);

    object o = reinterpret_cast<object>(t->backupHeap + t->backupHeapIndex);
    t->backupHeapIndex += ceilingDivide(sizeInBytes, BytesPerWord);
    fieldAtOffset<object>(o, 0) = 0;
    return o;
  } else if (UNLIKELY(t->getFlags() & Thread::TracingFlag)) {
    expect(t,
           t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
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
          > ThreadHeapSizeInWords) {
        t->heap = 0;
        if ((not t->m->heap->limitExceeded())
            and t->m->heapPoolIndex < ThreadHeapPoolSize) {
          t->heap = static_cast<uintptr_t*>(
              t->m->heap->tryAllocate(ThreadHeapSizeInBytes));

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
      if (t->m->fixedFootprint + sizeInBytes > FixedFootprintThresholdInBytes) {
        t->heap = 0;
      }
      break;

    case Machine::ImmortalAllocation:
      break;
    }

    int pendingAllocation = t->m->heap->fixedFootprint(
        ceilingDivide(sizeInBytes, BytesPerWord), objectMask);

    if (t->heap == 0 or t->m->heap->limitExceeded(pendingAllocation)) {
      //     fprintf(stderr, "gc");
      //     vmPrintTrace(t);
      collect(t, Heap::MinorCollection, pendingAllocation);
    }

    if (t->m->heap->limitExceeded(pendingAllocation)) {
      throw_(t, roots(t)->outOfMemoryError());
    }
  } while (type == Machine::MovableAllocation
           and t->heapIndex + ceilingDivide(sizeInBytes, BytesPerWord)
               > ThreadHeapSizeInWords);

  switch (type) {
  case Machine::MovableAllocation: {
    return allocateSmall(t, sizeInBytes);
  }

  case Machine::FixedAllocation: {
    object o = static_cast<object>(t->m->heap->allocateFixed(
        allocator, ceilingDivide(sizeInBytes, BytesPerWord), objectMask));

    memset(o, 0, sizeInBytes);

    alias(o, 0) = FixedMark;

    t->m->fixedFootprint += t->m->heap->fixedFootprint(
        ceilingDivide(sizeInBytes, BytesPerWord), objectMask);

    return o;
  }

  case Machine::ImmortalAllocation: {
    object o = static_cast<object>(t->m->heap->allocateImmortalFixed(
        allocator, ceilingDivide(sizeInBytes, BytesPerWord), objectMask));

    memset(o, 0, sizeInBytes);

    alias(o, 0) = FixedMark;

    return o;
  }

  default:
    abort(t);
  }
}

void collect(Thread* t, Heap::CollectionType type, int pendingAllocation)
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

object makeNewGeneral(Thread* t, GcClass* class_)
{
  assertT(t, t->state == Thread::ActiveState);

  PROTECT(t, class_);

  object instance = makeNew(t, class_);
  PROTECT(t, instance);

  if (class_->vmFlags() & WeakReferenceFlag) {
    ACQUIRE(t, t->m->referenceLock);

    cast<GcJreference>(t, instance)->vmNext() = t->m->weakReferences;
    t->m->weakReferences = cast<GcJreference>(t, instance);
  }

  if (class_->vmFlags() & HasFinalizerFlag) {
    addFinalizer(t, instance, 0);
  }

  return instance;
}

void popResources(Thread* t)
{
  while (t->resource != t->checkpoint->resource) {
    Thread::Resource* r = t->resource;
    t->resource = r->next;
    r->release();
  }

  t->protector = t->checkpoint->protector;
}

GcByteArray* makeByteArrayV(Thread* t, const char* format, va_list a, int size)
{
  THREAD_RUNTIME_ARRAY(t, char, buffer, size);

  int r = vm::vsnprintf(RUNTIME_ARRAY_BODY(buffer), size - 1, format, a);
  if (r >= 0 and r < size - 1) {
    GcByteArray* s = makeByteArray(t, strlen(RUNTIME_ARRAY_BODY(buffer)) + 1);
    memcpy(s->body().begin(), RUNTIME_ARRAY_BODY(buffer), s->length());
    return s;
  } else {
    return 0;
  }
}

GcByteArray* makeByteArray(Thread* t, const char* format, ...)
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

GcString* makeString(Thread* t, const char* format, ...)
{
  int size = 256;
  while (true) {
    va_list a;
    va_start(a, format);
    GcByteArray* s = makeByteArrayV(t, format, a, size);
    va_end(a);

    if (s) {
      return t->m->classpath->makeString(t, s, 0, s->length() - 1);
    } else {
      size *= 2;
    }
  }
}

int stringUTFLength(Thread* t,
                    GcString* string,
                    unsigned start,
                    unsigned length)
{
  unsigned result = 0;

  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      result = length;
    } else {
      GcCharArray* a = cast<GcCharArray>(t, data);
      for (unsigned i = 0; i < length; ++i) {
        uint16_t c = a->body()[string->offset(t) + start + i];
        if (c == 0)
          result += 1;  // null char (was 2 bytes in Java)
        else if (c < 0x80)
          result += 1;  // ASCII char
        else if (c < 0x800)
          result += 2;  // two-byte char
        else
          result += 3;  // three-byte char
      }
    }
  }

  return result;
}

void stringChars(Thread* t,
                 GcString* string,
                 unsigned start,
                 unsigned length,
                 char* chars)
{
  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      GcByteArray* b = cast<GcByteArray>(t, data);
      memcpy(chars, &b->body()[string->offset(t) + start], length);
    } else {
      GcCharArray* c = cast<GcCharArray>(t, data);
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = c->body()[string->offset(t) + start + i];
      }
    }
  }
  chars[length] = 0;
}

void stringChars(Thread* t,
                 GcString* string,
                 unsigned start,
                 unsigned length,
                 uint16_t* chars)
{
  if (length) {
    object data = string->data();
    if (objectClass(t, data) == type(t, GcByteArray::Type)) {
      GcByteArray* b = cast<GcByteArray>(t, data);
      for (unsigned i = 0; i < length; ++i) {
        chars[i] = b->body()[string->offset(t) + start + i];
      }
    } else {
      GcCharArray* c = cast<GcCharArray>(t, data);
      memcpy(chars,
             &c->body()[string->offset(t) + start],
             length * sizeof(uint16_t));
    }
  }
  chars[length] = 0;
}

void stringUTFChars(Thread* t,
                    GcString* string,
                    unsigned start,
                    unsigned length,
                    char* chars,
                    unsigned charsLength UNUSED)
{
  assertT(t,
          static_cast<unsigned>(stringUTFLength(t, string, start, length))
          == charsLength);

  object data = string->data();
  if (objectClass(t, data) == type(t, GcByteArray::Type)) {
    GcByteArray* b = cast<GcByteArray>(t, data);
    memcpy(chars, &b->body()[string->offset(t) + start], length);
    chars[length] = 0;
  } else {
    GcCharArray* cs = cast<GcCharArray>(t, data);
    int j = 0;
    for (unsigned i = 0; i < length; ++i) {
      uint16_t c = cs->body()[string->offset(t) + start + i];
      if (!c) {  // null char
        chars[j++] = 0;
      } else if (c < 0x80) {  // ASCII char
        chars[j++] = static_cast<char>(c);
      } else if (c < 0x800) {  // two-byte char
        chars[j++] = static_cast<char>(0x0c0 | (c >> 6));
        chars[j++] = static_cast<char>(0x080 | (c & 0x03f));
      } else {  // three-byte char
        chars[j++] = static_cast<char>(0x0e0 | ((c >> 12) & 0x0f));
        chars[j++] = static_cast<char>(0x080 | ((c >> 6) & 0x03f));
        chars[j++] = static_cast<char>(0x080 | (c & 0x03f));
      }
    }
    chars[j] = 0;
  }
}

uint64_t resolveBootstrap(Thread* t, uintptr_t* arguments)
{
  GcByteArray* name
      = cast<GcByteArray>(t, reinterpret_cast<object>(arguments[0]));

  resolveSystemClass(t, roots(t)->bootLoader(), name);

  return 1;
}

bool isAssignableFrom(Thread* t, GcClass* a, GcClass* b)
{
  assertT(t, a);
  assertT(t, b);

  if (a == b)
    return true;

  if (a->flags() & ACC_INTERFACE) {
    if (b->vmFlags() & BootstrapFlag) {
      uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(b->name())};

      if (run(t, resolveBootstrap, arguments) == 0) {
        t->exception = 0;
        return false;
      }
    }

    GcArray* itable = cast<GcArray>(t, b->interfaceTable());
    if (itable) {
      unsigned stride = (b->flags() & ACC_INTERFACE) ? 1 : 2;
      for (unsigned i = 0; i < itable->length(); i += stride) {
        if (itable->body()[i] == a) {
          return true;
        }
      }
    }
  } else if (a->arrayDimensions()) {
    if (b->arrayDimensions()) {
      return isAssignableFrom(
          t, a->arrayElementClass(), b->arrayElementClass());
    }
  } else if ((a->vmFlags() & PrimitiveFlag) == (b->vmFlags() & PrimitiveFlag)) {
    for (; b; b = b->super()) {
      if (b == a) {
        return true;
      }
    }
  }

  return false;
}

bool instanceOf(Thread* t, GcClass* class_, object o)
{
  if (o == 0) {
    return false;
  } else {
    return isAssignableFrom(t, class_, objectClass(t, o));
  }
}

GcMethod* classInitializer(Thread* t, GcClass* class_)
{
  if (GcArray* mtable = cast<GcArray>(t, class_->methodTable())) {
    PROTECT(t, mtable);
    for (unsigned i = 0; i < mtable->length(); ++i) {
      GcMethod* o = cast<GcMethod>(t, mtable->body()[i]);

      if (o->vmFlags() & ClassInitFlag) {
        return o;
      }
    }
  }
  return 0;
}

unsigned fieldCode(Thread* t, unsigned javaCode)
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

  default:
    abort(t);
  }
}

unsigned fieldType(Thread* t, unsigned code)
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

  default:
    abort(t);
  }
}

unsigned primitiveSize(Thread* t, unsigned code)
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

  default:
    abort(t);
  }
}

GcClass* parseClass(Thread* t,
                    GcClassLoader* loader,
                    const uint8_t* data,
                    unsigned size,
                    Gc::Type throwType)
{
  PROTECT(t, loader);

  class Client : public Stream::Client {
   public:
    Client(Thread* t) : t(t)
    {
    }

    virtual void NO_RETURN handleError()
    {
      abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, data, size);

  uint32_t magic = s.read4();
  expect(t, magic == 0xCAFEBABE);
  unsigned minorVer = s.read2();  // minor version
  unsigned majorVer = s.read2();  // major version
  if (DebugClassReader) {
    fprintf(stderr, "read class (minor %d major %d)\n", minorVer, majorVer);
  }

  GcSingleton* pool = parsePool(t, s);
  PROTECT(t, pool);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  GcClass* class_ = (GcClass*)makeClass(
      t,
      flags,
      0,  // VM flags
      0,  // fixed size
      0,  // array size
      0,  // array dimensions
      0,  // array element class
      0,  // runtime data index
      0,  // object mask
      cast<GcReference>(t, singletonObject(t, pool, name - 1))->name(),
      0,  // source file
      0,  // super
      0,  // interfaces
      0,  // vtable
      0,  // fields
      0,  // methods
      0,  // addendum
      0,  // static table
      loader,
      0,   // source
      0);  // vtable length
  PROTECT(t, class_);

  unsigned super = s.read2();
  if (super) {
    GcClass* sc = resolveClass(
        t,
        loader,
        cast<GcReference>(t, singletonObject(t, pool, super - 1))->name(),
        true,
        throwType);

    class_->setSuper(t, sc);

    class_->vmFlags() |= (sc->vmFlags() & (ReferenceFlag | WeakReferenceFlag
                                           | HasFinalizerFlag | NeedInitFlag));
  }

  if (DebugClassReader) {
    fprintf(stderr, "  flags %d name %d super %d\n", flags, name, super);
  }

  parseInterfaceTable(t, s, class_, pool, throwType);

  parseFieldTable(t, s, class_, pool);

  parseMethodTable(t, s, class_, pool);

  parseAttributeTable(t, s, class_, pool);

  GcArray* vtable = cast<GcArray>(t, class_->virtualTable());
  unsigned vtableLength = (vtable ? vtable->length() : 0);

  GcClass* real = t->m->processor->makeClass(t,
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

  if (roots(t)->poolMap()) {
    object bootstrapClass = hashMapFind(t,
                                        roots(t)->bootstrapClassMap(),
                                        class_->name(),
                                        byteArrayHash,
                                        byteArrayEqual);

    hashMapInsert(t,
                  roots(t)->poolMap(),
                  bootstrapClass ? bootstrapClass : real,
                  pool,
                  objectHash);
  }

  return real;
}

uint64_t runParseClass(Thread* t, uintptr_t* arguments)
{
  GcClassLoader* loader
      = cast<GcClassLoader>(t, reinterpret_cast<object>(arguments[0]));
  System::Region* region = reinterpret_cast<System::Region*>(arguments[1]);
  Gc::Type throwType = static_cast<Gc::Type>(arguments[2]);

  return reinterpret_cast<uintptr_t>(
      parseClass(t, loader, region->start(), region->length(), throwType));
}

GcClass* resolveSystemClass(Thread* t,
                            GcClassLoader* loader,
                            GcByteArray* spec,
                            bool throw_,
                            Gc::Type throwType)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  /*
   * We require that SystemClassLoader.isForbidden() has already
   * been executed once before isBlacklisting is set to true.
   * Otherwise this code-block recurses until the stack explodes.
   */
  if (t->isBlacklisting()
        && ::strcmp("avian/SystemClassLoader", reinterpret_cast<const char*>(spec->body().begin()))) {
    GcMethod* forbid = resolveMethod(t,
                                     roots(t)->bootLoader(),
                                     "avian/SystemClassLoader",
                                     "isForbidden",
                                     "(Ljava/lang/String;)Z");
    GcString *name = t->m->classpath->makeString(t, spec, 0, spec->length());
    GcInt *result = cast<GcInt>(t, t->m->processor->invoke(t, forbid, NULL, name));
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

    if (result->value() == JNI_TRUE) {
      if (throw_) {
        throwNew(t, throwType, "%s", spec->body().begin());
      } else {
        return 0;
      }
    }
  }

  GcClass* class_ = findLoadedClass(t, loader, spec);
  if (class_ == 0) {
    PROTECT(t, class_);

    if (loader->parent()) {
      class_ = resolveSystemClass(t, loader->parent(), spec, false);
      if (class_) {
        return class_;
      }
    }

    if (spec->body()[0] == '[') {
      class_ = resolveArrayClass(t, loader, spec, throw_, throwType);
    } else {
      GcSystemClassLoader* sysLoader = loader->as<GcSystemClassLoader>(t);
      PROTECT(t, sysLoader);

      THREAD_RUNTIME_ARRAY(t, char, file, spec->length() + 6);
      memcpy(
          RUNTIME_ARRAY_BODY(file), spec->body().begin(), spec->length() - 1);
      memcpy(RUNTIME_ARRAY_BODY(file) + spec->length() - 1, ".class", 7);

      System::Region* region = static_cast<Finder*>(sysLoader->finder())
                                   ->find(RUNTIME_ARRAY_BODY(file));

      if (region) {
        if (Verbose) {
          fprintf(stderr, "parsing %s\n", spec->body().begin());
        }

        {
          THREAD_RESOURCE(t, System::Region*, region, region->dispose());

          uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(loader),
                                   reinterpret_cast<uintptr_t>(region),
                                   static_cast<uintptr_t>(throwType)};

          // parse class file
          class_ = cast<GcClass>(
              t, reinterpret_cast<object>(runRaw(t, runParseClass, arguments)));

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
          fprintf(
              stderr, "done parsing %s: %p\n", spec->body().begin(), class_);
        }

        {
          const char* source = static_cast<Finder*>(sysLoader->finder())
                                   ->sourceUrl(RUNTIME_ARRAY_BODY(file));

          if (source) {
            unsigned length = strlen(source);
            GcByteArray* array = makeByteArray(t, length + 1);
            memcpy(array->body().begin(), source, length);
            array = internByteArray(t, array);

            class_->setSource(t, array);
          }
        }

        GcClass* bootstrapClass
            = cast<GcClass>(t,
                            hashMapFind(t,
                                        roots(t)->bootstrapClassMap(),
                                        spec,
                                        byteArrayHash,
                                        byteArrayEqual));

        if (bootstrapClass) {
          PROTECT(t, bootstrapClass);

          updateBootstrapClass(t, bootstrapClass, class_);
          class_ = bootstrapClass;
        }
      }
    }

    if (class_) {
      hashMapInsert(
          t, cast<GcHashMap>(t, loader->map()), spec, class_, byteArrayHash);

      updatePackageMap(t, class_);
    } else if (throw_) {
      throwNew(t, throwType, "%s", spec->body().begin());
    }
  }

  return class_;
}

GcClass* findLoadedClass(Thread* t, GcClassLoader* loader, GcByteArray* spec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  ACQUIRE(t, t->m->classLock);

  return loader->map()
             ? cast<GcClass>(t,
                             hashMapFind(t,
                                         cast<GcHashMap>(t, loader->map()),
                                         spec,
                                         byteArrayHash,
                                         byteArrayEqual))
             : 0;
}

GcClass* resolveClass(Thread* t,
                      GcClassLoader* loader,
                      GcByteArray* spec,
                      bool throw_,
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
      if (roots(t)->loadClassMethod() == 0) {
        GcMethod* m = resolveMethod(t,
                                    roots(t)->bootLoader(),
                                    "java/lang/ClassLoader",
                                    "loadClass",
                                    "(Ljava/lang/String;)Ljava/lang/Class;");

        if (m) {
          roots(t)->setLoadClassMethod(t, m);

          GcClass* classLoaderClass = type(t, GcClassLoader::Type);

          if (classLoaderClass->vmFlags() & BootstrapFlag) {
            resolveSystemClass(
                t, roots(t)->bootLoader(), classLoaderClass->name());
          }
        }
      }

      GcMethod* method = findVirtualMethod(
          t, roots(t)->loadClassMethod(), objectClass(t, loader));

      PROTECT(t, method);

      THREAD_RUNTIME_ARRAY(t, char, s, spec->length());
      replace('/',
              '.',
              RUNTIME_ARRAY_BODY(s),
              reinterpret_cast<char*>(spec->body().begin()));

      GcString* specString = makeString(t, "%s", RUNTIME_ARRAY_BODY(s));
      PROTECT(t, specString);

      uintptr_t arguments[] = {reinterpret_cast<uintptr_t>(method),
                               reinterpret_cast<uintptr_t>(loader),
                               reinterpret_cast<uintptr_t>(specString)};

      GcJclass* jc = cast<GcJclass>(
          t, reinterpret_cast<object>(runRaw(t, invokeLoadClass, arguments)));

      if (LIKELY(jc)) {
        c = jc->vmClass();
      } else if (t->exception) {
        if (throw_) {
          GcThrowable* e
              = type(t, throwType) == objectClass(t, t->exception)
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
      throwNew(t, throwType, "%s", spec->body().begin());
    }

    return c;
  }
}

GcMethod* resolveMethod(Thread* t,
                        GcClass* class_,
                        const char* methodName,
                        const char* methodSpec)
{
  PROTECT(t, class_);

  GcByteArray* name = makeByteArray(t, methodName);
  PROTECT(t, name);

  GcByteArray* spec = makeByteArray(t, methodSpec);

  GcMethod* method
      = cast<GcMethod>(t, findMethodInClass(t, class_, name, spec));

  if (method == 0) {
    throwNew(t,
             GcNoSuchMethodError::Type,
             "%s %s not found in %s",
             methodName,
             methodSpec,
             class_->name()->body().begin());
  } else {
    return method;
  }
}

GcField* resolveField(Thread* t,
                      GcClass* class_,
                      const char* fieldName,
                      const char* fieldSpec)
{
  PROTECT(t, class_);

  GcByteArray* name = makeByteArray(t, fieldName);
  PROTECT(t, name);

  GcByteArray* spec = makeByteArray(t, fieldSpec);
  PROTECT(t, spec);

  GcField* field = cast<GcField>(
      t, findInInterfaces(t, class_, name, spec, findFieldInClass));

  GcClass* c = class_;
  PROTECT(t, c);

  for (; c != 0 and field == 0; c = c->super()) {
    field = cast<GcField>(t, findFieldInClass(t, c, name, spec));
  }

  if (field == 0) {
    throwNew(t,
             GcNoSuchFieldError::Type,
             "%s %s not found in %s",
             fieldName,
             fieldSpec,
             class_->name()->body().begin());
  } else {
    return field;
  }
}

bool classNeedsInit(Thread* t, GcClass* c)
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

bool preInitClass(Thread* t, GcClass* c)
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
        throwNew(
            t, GcNoClassDefFoundError::Type, "%s", c->name()->body().begin());
      } else {
        c->vmFlags() |= InitFlag;
        return true;
      }
    }
  }
  return false;
}

void postInitClass(Thread* t, GcClass* c)
{
  PROTECT(t, c);
  ACQUIRE(t, t->m->classLock);

  if (t->exception
      and instanceOf(t, type(t, GcException::Type), t->exception)) {
    c->vmFlags() |= NeedInitFlag | InitErrorFlag;
    c->vmFlags() &= ~InitFlag;

    GcThrowable* exception = t->exception;
    t->exception = 0;

    GcExceptionInInitializerError* initExecption
        = makeThrowable(t, GcExceptionInInitializerError::Type, 0, 0, exception)
              ->as<GcExceptionInInitializerError>(t);

    initExecption->setException(t, exception->cause());

    throw_(t, initExecption->as<GcThrowable>(t));
  } else {
    c->vmFlags() &= ~(NeedInitFlag | InitFlag);
  }
  t->m->classLock->notifyAll(t->systemThread);
}

void initClass(Thread* t, GcClass* c)
{
  PROTECT(t, c);

  GcClass* super = c->super();
  if (super) {
    initClass(t, super);
  }

  if (preInitClass(t, c)) {
    OBJECT_RESOURCE(t, c, postInitClass(t, cast<GcClass>(t, c)));

    GcMethod* initializer = classInitializer(t, c);

    if (initializer) {
      Thread::ClassInitStack stack(t, c);

      t->m->processor->invoke(t, initializer, 0);
    }
  }
}

GcClass* resolveObjectArrayClass(Thread* t,
                                 GcClassLoader* loader,
                                 GcClass* elementClass)
{
  PROTECT(t, loader);
  PROTECT(t, elementClass);

  {
    GcClass* arrayClass
        = cast<GcClass>(t, getClassRuntimeData(t, elementClass)->arrayClass());
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
    memcpy(
        &spec->body()[1], elementSpec->body().begin(), elementSpec->length());
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

  getClassRuntimeData(t, elementClass)->setArrayClass(t, arrayClass);

  return arrayClass;
}

object makeObjectArray(Thread* t, GcClass* elementClass, unsigned count)
{
  GcClass* arrayClass
      = resolveObjectArrayClass(t, elementClass->loader(), elementClass);

  PROTECT(t, arrayClass);

  object array = makeArray(t, count);
  setObjectClass(t, array, arrayClass);

  return array;
}

static GcByteArray* getFieldName(Thread* t, object obj)
{
  return reinterpret_cast<GcByteArray*>(cast<GcField>(t, obj)->name());
}

static GcByteArray* getFieldSpec(Thread* t, object obj)
{
  return reinterpret_cast<GcByteArray*>(cast<GcField>(t, obj)->spec());
}

static GcByteArray* getMethodName(Thread* t, object obj)
{
  return reinterpret_cast<GcByteArray*>(cast<GcMethod>(t, obj)->name());
}

static GcByteArray* getMethodSpec(Thread* t, object obj)
{
  return reinterpret_cast<GcByteArray*>(cast<GcMethod>(t, obj)->spec());
}

object findFieldInClass(Thread* t,
                        GcClass* class_,
                        GcByteArray* name,
                        GcByteArray* spec)
{
  return findInTable(t,
                     cast<GcArray>(t, class_->fieldTable()),
                     name,
                     spec,
                     getFieldName,
                     getFieldSpec);
}

object findMethodInClass(Thread* t,
                         GcClass* class_,
                         GcByteArray* name,
                         GcByteArray* spec)
{
  return findInTable(t,
                     cast<GcArray>(t, class_->methodTable()),
                     name,
                     spec,
                     getMethodName,
                     getMethodSpec);
}

object findInHierarchyOrNull(
    Thread* t,
    GcClass* class_,
    GcByteArray* name,
    GcByteArray* spec,
    object (*find)(Thread*, GcClass*, GcByteArray*, GcByteArray*))
{
  GcClass* originalClass = class_;

  object o = 0;
  if ((class_->flags() & ACC_INTERFACE) and class_->virtualTable()) {
    o = findInTable(t,
                    cast<GcArray>(t, class_->virtualTable()),
                    name,
                    spec,
                    getMethodName,
                    getMethodSpec);
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

unsigned parameterFootprint(Thread* t, const char* s, bool static_)
{
  unsigned footprint = 0;
  for (MethodSpecIterator it(t, s); it.hasNext();) {
    switch (*it.next()) {
    case 'J':
    case 'D':
      footprint += 2;
      break;

    default:
      ++footprint;
      break;
    }
  }

  if (not static_) {
    ++footprint;
  }
  return footprint;
}

void addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object))
{
  PROTECT(t, target);

  ACQUIRE(t, t->m->referenceLock);

  void* function;
  memcpy(&function, &finalize, BytesPerWord);

  GcFinalizer* f = makeFinalizer(t, 0, function, 0, 0, 0);
  f->target() = target;
  f->next() = t->m->finalizers;
  t->m->finalizers = f;
}

GcMonitor* objectMonitor(Thread* t, object o, bool createNew)
{
  assertT(t, t->state == Thread::ActiveState);

  object m = hashMapFind(t, roots(t)->monitorMap(), o, objectHash, objectEqual);

  if (m) {
    if (DebugMonitors) {
      fprintf(stderr, "found monitor %p for object %x\n", m, objectHash(t, o));
    }

    return cast<GcMonitor>(t, m);
  } else if (createNew) {
    PROTECT(t, o);
    PROTECT(t, m);

    {
      ENTER(t, Thread::ExclusiveState);

      m = hashMapFind(t, roots(t)->monitorMap(), o, objectHash, objectEqual);

      if (m) {
        if (DebugMonitors) {
          fprintf(
              stderr, "found monitor %p for object %x\n", m, objectHash(t, o));
        }

        return cast<GcMonitor>(t, m);
      }

      object head = makeMonitorNode(t, 0, 0);
      m = makeMonitor(t, 0, 0, 0, head, head, 0);

      if (DebugMonitors) {
        fprintf(stderr, "made monitor %p for object %x\n", m, objectHash(t, o));
      }

      hashMapInsert(t, roots(t)->monitorMap(), o, m, objectHash);

      addFinalizer(t, o, removeMonitor);
    }

    return cast<GcMonitor>(t, m);
  } else {
    return 0;
  }
}

object intern(Thread* t, object s)
{
  PROTECT(t, s);

  ACQUIRE(t, t->m->referenceLock);

  GcTriple* n
      = hashMapFindNode(t, roots(t)->stringMap(), s, stringHash, stringEqual);

  if (n) {
    return cast<GcJreference>(t, n->first())->target();
  } else {
    hashMapInsert(t, roots(t)->stringMap(), s, 0, stringHash);
    addFinalizer(t, s, removeString);
    return s;
  }
}

object clone(Thread* t, object o)
{
  PROTECT(t, o);

  GcClass* class_ = objectClass(t, o);
  unsigned size = baseSize(t, o, class_) * BytesPerWord;
  object clone;

  if (class_->arrayElementSize()) {
    clone = static_cast<object>(allocate(t, size, class_->objectMask()));
    memcpy(clone, o, size);
    // clear any object header flags:
    setObjectClass(t, o, objectClass(t, o));
  } else if (instanceOf(t, type(t, GcCloneable::Type), o)) {
    clone = make(t, class_);
    memcpy(reinterpret_cast<void**>(clone) + 1,
           reinterpret_cast<void**>(o) + 1,
           size - BytesPerWord);
  } else {
    GcByteArray* classNameSlash = objectClass(t, o)->name();
    THREAD_RUNTIME_ARRAY(t, char, classNameDot, classNameSlash->length());
    replace('/',
            '.',
            RUNTIME_ARRAY_BODY(classNameDot),
            reinterpret_cast<char*>(classNameSlash->body().begin()));
    throwNew(t,
             GcCloneNotSupportedException::Type,
             "%s",
             RUNTIME_ARRAY_BODY(classNameDot));
  }

  return clone;
}

void walk(Thread* t, Heap::Walker* w, object o, unsigned start)
{
  GcClass* class_ = t->m->heap->follow(objectClass(t, o));
  GcIntArray* objectMask = t->m->heap->follow(class_->objectMask());

  bool more = true;

  if (objectMask) {
    unsigned fixedSize = class_->fixedSize();
    unsigned arrayElementSize = class_->arrayElementSize();
    unsigned arrayLength = (arrayElementSize ? fieldAtOffset<uintptr_t>(
                                                   o, fixedSize - BytesPerWord)
                                             : 0);

    THREAD_RUNTIME_ARRAY(t, uint32_t, mask, objectMask->length());
    memcpy(RUNTIME_ARRAY_BODY(mask),
           objectMask->body().begin(),
           objectMask->length() * 4);

    more = ::walk(t,
                  w,
                  RUNTIME_ARRAY_BODY(mask),
                  fixedSize,
                  arrayElementSize,
                  arrayLength,
                  start);
  } else if (class_->vmFlags() & SingletonFlag) {
    GcSingleton* s = cast<GcSingleton>(t, o);
    unsigned length = s->length();
    if (length) {
      more = ::walk(t,
                    w,
                    singletonMask(t, s),
                    (singletonCount(t, s) + 2) * BytesPerWord,
                    0,
                    0,
                    start);
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

int walkNext(Thread* t, object o, int previous)
{
  class Walker : public Heap::Walker {
   public:
    Walker() : value(-1)
    {
    }

    bool visit(unsigned offset)
    {
      value = offset;
      return false;
    }

    int value;
  } walker;

  walk(t, &walker, o, previous + 1);
  return walker.value;
}

void visitRoots(Machine* m, Heap::Visitor* v)
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

void logTrace(FILE* f, const char* fmt, ...)
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

void printTrace(Thread* t, GcThrowable* exception)
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
        GcTraceElement* e
            = cast<GcTraceElement>(t, objectArrayBody(t, trace, i));
        GcMethod* m = cast<GcMethod>(t, e->method());
        const int8_t* class_ = m->class_()->name()->body().begin();
        const int8_t* method = m->name()->body().begin();
        int line = t->m->processor->lineNumber(t, m, e->ip());

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

object makeTrace(Thread* t, Processor::StackWalker* walker)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t) : t(t), trace(0), index(0), protector(t, &trace)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      if (trace == 0) {
        trace = makeObjectArray(t, walker->count());
        assertT(t, trace);
      }

      GcTraceElement* e = makeTraceElement(t, walker->method(), walker->ip());
      assertT(t, index < objectArrayLength(t, trace));
      reinterpret_cast<GcArray*>(trace)->setBodyElement(t, index, e);
      ++index;
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

object makeTrace(Thread* t, Thread* target)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t) : t(t), trace(0)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      trace = vm::makeTrace(t, walker);
      return false;
    }

    Thread* t;
    object trace;
  } v(t);

  t->m->processor->walkStack(target, &v);

  return v.trace ? v.trace : makeObjectArray(t, 0);
}

void runFinalizeThread(Thread* t)
{
  GcFinalizer* finalizeList = 0;
  PROTECT(t, finalizeList);

  GcCleaner* cleanList = 0;
  PROTECT(t, cleanList);

  while (true) {
    {
      ACQUIRE(t, t->m->stateLock);

      while (t->m->finalizeThread and roots(t)->objectsToFinalize() == 0
             and roots(t)->objectsToClean() == 0) {
        ENTER(t, Thread::IdleState);
        t->m->stateLock->wait(t->systemThread, 0);
      }

      if (t->m->finalizeThread == 0) {
        return;
      } else {
        finalizeList = roots(t)->objectsToFinalize();
        roots(t)->setObjectsToFinalize(t, 0);

        cleanList = roots(t)->objectsToClean();
        roots(t)->setObjectsToClean(t, 0);
      }
    }

    for (; finalizeList; finalizeList = finalizeList->queueNext()) {
      finalizeObject(t, finalizeList->queueTarget(), "finalize");
    }

    for (; cleanList; cleanList = cleanList->queueNext()) {
      finalizeObject(t, cleanList, "clean");
    }
  }
}

object parseUtf8(Thread* t, const char* data, unsigned length)
{
  class Client : public Stream::Client {
   public:
    Client(Thread* t) : t(t)
    {
    }

    virtual void handleError()
    {
      if (false)
        abort(t);
    }

   private:
    Thread* t;
  } client(t);

  Stream s(&client, reinterpret_cast<const uint8_t*>(data), length);

  return ::parseUtf8(t, s, length);
}

object parseUtf8(Thread* t, GcByteArray* array)
{
  for (unsigned i = 0; i < array->length() - 1; ++i) {
    if (array->body()[i] & 0x80) {
      goto slow_path;
    }
  }

  return array;

slow_path:
  class Client : public Stream::Client {
   public:
    Client(Thread* t) : t(t)
    {
    }

    virtual void handleError()
    {
      if (false)
        abort(t);
    }

   private:
    Thread* t;
  } client(t);

  class MyStream : public AbstractStream {
   public:
    class MyProtector : public Thread::Protector {
     public:
      MyProtector(Thread* t, MyStream* s) : Protector(t), s(s)
      {
      }

      virtual void visit(Heap::Visitor* v)
      {
        v->visit(&(s->array));
      }

      MyStream* s;
    };

    MyStream(Thread* t, Client* client, GcByteArray* array)
        : AbstractStream(client, array->length() - 1),
          array(array),
          protector(t, this)
    {
    }

    virtual void copy(uint8_t* dst, unsigned offset, unsigned size)
    {
      memcpy(dst, &array->body()[offset], size);
    }

    GcByteArray* array;
    MyProtector protector;
  } s(t, &client, array);

  return ::parseUtf8(t, s, array->length() - 1);
}

GcMethod* getCaller(Thread* t, unsigned target, bool skipMethodInvoke)
{
  if (static_cast<int>(target) == -1) {
    target = 2;
  }

  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t, unsigned target, bool skipMethodInvoke)
        : t(t),
          method(0),
          count(0),
          target(target),
          skipMethodInvoke(skipMethodInvoke)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      if (skipMethodInvoke
          and walker->method()->class_() == type(t, GcJmethod::Type)
          and strcmp(walker->method()->name()->body().begin(),
                     reinterpret_cast<const int8_t*>("invoke")) == 0) {
        return true;
      }

      if (count == target) {
        method = walker->method();
        return false;
      } else {
        ++count;
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

GcClass* defineClass(Thread* t,
                     GcClassLoader* loader,
                     const uint8_t* buffer,
                     unsigned length)
{
  PROTECT(t, loader);

  GcClass* c = parseClass(t, loader, buffer, length);

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

void populateMultiArray(Thread* t,
                        object array,
                        int32_t* counts,
                        unsigned index,
                        unsigned dimensions)
{
  if (index + 1 == dimensions or counts[index] == 0) {
    return;
  }

  PROTECT(t, array);

  GcByteArray* spec = objectClass(t, array)->name();
  PROTECT(t, spec);

  GcByteArray* elementSpec = makeByteArray(t, spec->length() - 1);
  memcpy(elementSpec->body().begin(), &spec->body()[1], spec->length() - 1);

  GcClass* class_
      = resolveClass(t, objectClass(t, array)->loader(), elementSpec);
  PROTECT(t, class_);

  for (int32_t i = 0; i < counts[index]; ++i) {
    GcArray* a = makeArray(
        t,
        ceilingDivide(counts[index + 1] * class_->arrayElementSize(),
                      BytesPerWord));
    a->length() = counts[index + 1];
    setObjectClass(t, a, class_);
    setField(t, array, ArrayBody + (i * BytesPerWord), a);

    populateMultiArray(t, a, counts, index + 1, dimensions);
  }
}

object interruptLock(Thread* t, GcThread* thread)
{
  object lock = thread->interruptLock();

  loadMemoryBarrier();

  if (lock == 0) {
    PROTECT(t, thread);
    ACQUIRE(t, t->m->referenceLock);

    if (thread->interruptLock() == 0) {
      object head = makeMonitorNode(t, 0, 0);
      GcMonitor* lock = makeMonitor(t, 0, 0, 0, head, head, 0);

      storeStoreMemoryBarrier();

      thread->setInterruptLock(t, lock);
    }
  }

  return thread->interruptLock();
}

void clearInterrupted(Thread* t)
{
  monitorAcquire(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
  t->javaThread->interrupted() = false;
  monitorRelease(t, cast<GcMonitor>(t, interruptLock(t, t->javaThread)));
}

void threadInterrupt(Thread* t, GcThread* thread)
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

bool threadIsInterrupted(Thread* t, GcThread* thread, bool clear)
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

GcJclass* getDeclaringClass(Thread* t, GcClass* c)
{
  GcClassAddendum* addendum = c->addendum();
  if (addendum) {
    GcArray* table = cast<GcArray>(t, addendum->innerClassTable());
    if (table) {
      for (unsigned i = 0; i < table->length(); ++i) {
        GcInnerClassReference* reference
            = cast<GcInnerClassReference>(t, table->body()[i]);
        if (reference->outer()
            and strcmp(reference->inner()->body().begin(),
                       c->name()->body().begin()) == 0) {
          return getJClass(t, resolveClass(t, c->loader(), reference->outer()));
        }
      }
    }
  }

  return 0;
}

// Called when interpreting invokedynamic. `invocation` points to
// static data in the bootstrap method table, which in turn points to
// a bootstrap method and stores additional data to be passed to
// it. `resolveDynamic` will then call this bootstrap method after
// resolving the arguments as required. The called method is assumed
// to be a lambda `metafactory` or `altMetafactory`.
//
// Note that capture/bridging etc happens within the bootstrap method,
// this is just the code that dispatches to it.
//
// Returns the CallSite returned by the bootstrap method.
GcCallSite* resolveDynamic(Thread* t, GcInvocation* invocation)
{
  PROTECT(t, invocation);

  // Use the invocation's Class to get the bootstrap method table and get a classloader.
  GcClass* c = invocation->class_();
  PROTECT(t, c);

  // First element points to the bootstrap method. The rest are static data passed to the BSM.
  GcCharArray* bootstrapArray = cast<GcCharArray>(
      t,
      cast<GcArray>(t, c->addendum()->bootstrapMethodTable())
          ->body()[invocation->bootstrap()]);

  PROTECT(t, bootstrapArray);

  // Resolve the bootstrap method itself.
  GcMethod* bootstrap = cast<GcMethodHandle>(t,
                                       resolve(t,
                                               c->loader(),
                                               invocation->pool(),
                                               bootstrapArray->body()[0],
                                               findMethodInClass,
                                               GcNoSuchMethodError::Type))->method();
  PROTECT(t, bootstrap);

  // Caller context info to be passed to the bootstrap method.
  GcLookup* lookup
      = makeLookup(t, c, ACC_PUBLIC | ACC_PRIVATE | ACC_PROTECTED | ACC_STATIC);
  PROTECT(t, lookup);

  // The name of the linked-to method.
  GcByteArray* nameBytes = invocation->template_()->name();
  GcString* name
      = t->m->classpath->makeString(t, nameBytes, 0, nameBytes->length() - 1);
  PROTECT(t, name);

  // This is the type of the linked-to method (e.g. lambda).
  GcMethodType* type = makeMethodType(
      t, c->loader(), invocation->template_()->spec(), 0, 0, 0);
  PROTECT(t, type);

  // `array` stores either
  // 1. All the arguments to be passed to the bootstrap method in the case of `metafactory`
  // 2. The vararg object array to be passed to `altMetafactory`
  GcArray* array = makeArray(t, bootstrap->parameterCount());
  PROTECT(t, array);

  // These are common arguments to metafactory and altMetafactory
  unsigned argument = 0;
  array->setBodyElement(t, argument++, lookup);
  array->setBodyElement(t, argument++, name);
  array->setBodyElement(t, argument++, type);

  THREAD_RUNTIME_ARRAY(t, char, specBuffer, bootstrap->spec()->length());

  const char* spec;
  // `argArray` stores the final arguments to be passed to the bootstrap method.
  // Later in this function we iterate through the method signature +
  // bootstrap array and resolve the arguments as required into `array`.
  //
  // In the case of a `metafactory` call:
  //   `argArray = [caller, invokedName, invokedType, methodType, methodImplementation, instantiatedType]`
  //   `array = argArray`
  //
  // In the case of an `altMetafactory` call:
  //   `argArray = [caller, invokedName, invokedType, array]`
  //   `array = [methodType, methodImplementation, instantiatedType, flags, ...]`
  GcArray* argArray = array;
  PROTECT(t, argArray);

  // Check if the bootstrap method's signature matches that of an altMetafactory
  if (::strcmp(reinterpret_cast<char*>(bootstrap->spec()->body().begin()),
               "(Ljava/lang/invoke/MethodHandles$Lookup;"
               "Ljava/lang/String;"
               "Ljava/lang/invoke/MethodType;"
               "[Ljava/lang/Object;)"
               "Ljava/lang/invoke/CallSite;") == 0) {
    // If so, create a new array to store the varargs in, and hardcode the BSM signature.
    array = makeArray(t, bootstrapArray->length() - 1);
    spec = "(Ljava/lang/invoke/MethodHandles$Lookup;"
      "Ljava/lang/String;"
      "Ljava/lang/invoke/MethodType;"
      "Ljava/lang/invoke/MethodType;"
      "Ljava/lang/invoke/MethodHandle;"
      "Ljava/lang/invoke/MethodType;"
      "I"
      "I"
      "[Ljava/lang/Class;"
      "I"
      "[Ljava/lang/invoke/MethodType;"
      ")Ljava/lang/invoke/CallSite;";
  } else if (bootstrap->parameterCount() == 2 + bootstrapArray->length()) {
    // We're calling the simpler `metafactory`. 2 + bootstrapArray->length() is the
    // arguments to the bootstrap method (bootstrapArray->length() - 1), plus the 3 static
    // arguments (lookup, name, type).
    memcpy(RUNTIME_ARRAY_BODY(specBuffer),
           bootstrap->spec()->body().begin(),
           bootstrap->spec()->length());
    spec = RUNTIME_ARRAY_BODY(specBuffer);
  } else {
    abort(t);
  }

  MethodSpecIterator it(t, spec);

  // Skip over the already handled 3 arguments.
  for (unsigned i = 0; i < argument; ++i)
    it.next();

  // If we're calling altMetafactory then we reset the argument
  // offset, because we are filling the vararg array instead of the
  // final argument array.
  if (argArray != array) {
    argument = 0;
  }

  // `i` iterates through the bootstrap arguments (the +1 is because we skip
  // the bootstrap method's name), `it` iterates through the corresponding types
  // in the method signature
  unsigned i = 0;
  while (i + 1 < bootstrapArray->length() && it.hasNext()) {
    const char* p = it.next();

    switch (*p) {
    case 'L': {
      const char* const methodType = "Ljava/lang/invoke/MethodType;";
      const char* const methodHandle = "Ljava/lang/invoke/MethodHandle;";
      if (strncmp(p, methodType, strlen(methodType)) == 0) {
        GcMethodType* type = makeMethodType(
            t,
            c->loader(),
            cast<GcByteArray>(
                t,
                singletonObject(
                    t, invocation->pool(), bootstrapArray->body()[i + 1])),
            0,
            0,
            0);

        array->setBodyElement(t, i + argument, type);
      } else if (strncmp(p, methodHandle, strlen(methodHandle)) == 0) {
        GcMethodHandle* handle = cast<GcMethodHandle>(t,
                                          resolve(t,
                                                  c->loader(),
                                                  invocation->pool(),
                                                  bootstrapArray->body()[i + 1],
                                                  findMethodInClass,
                                                  GcNoSuchMethodError::Type));

        array->setBodyElement(t, i + argument, handle);
      } else {
        abort(t);
      }
    } break;

    case 'I':
    case 'F': {
      GcInt* box = makeInt(
          t,
          singletonValue(t, invocation->pool(), bootstrapArray->body()[i + 1]));

      array->setBodyElement(t, i + argument, box);
    } break;

    case 'J':
    case 'D': {
      uint64_t v;
      memcpy(
          &v,
          &singletonValue(t, invocation->pool(), bootstrapArray->body()[i + 1]),
          8);

      GcLong* box = makeLong(t, v);

      array->setBodyElement(t, i + argument, box);
    } break;

    default:
      fprintf(stderr, "todo: unsupported bootstrap argument type: %s", p);
      abort(t);
    }

    ++i;
  }

  GcMethodHandle* handle
      = (bootstrap->flags() & ACC_STATIC)
            ? 0
            : makeMethodHandle(t, REF_invokeSpecial, c->loader(), bootstrap, 0);

  // If we're calling altMetafactory we set the fourth argument to the vararg array.
  if (argArray != array) {
    argArray->setBodyElement(t, 3, array);
  }

  // Finally we make the bootstrap call.
  return cast<GcCallSite>(
      t, t->m->processor->invokeArray(t, bootstrap, handle, argArray));
}

void noop()
{
}

#include "type-constructors.cpp"

}  // namespace vm

// for debugging
AVIAN_EXPORT void vmfPrintTrace(Thread* t, FILE* out)
{
  class Visitor : public Processor::StackVisitor {
   public:
    Visitor(Thread* t, FILE* out) : t(t), out(out)
    {
    }

    virtual bool visit(Processor::StackWalker* walker)
    {
      const int8_t* class_ = walker->method()->class_()->name()->body().begin();
      const int8_t* method = walker->method()->name()->body().begin();
      int line = t->m->processor->lineNumber(t, walker->method(), walker->ip());

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

AVIAN_EXPORT void vmPrintTrace(Thread* t)
{
  vmfPrintTrace(t, stderr);
}

// also for debugging
AVIAN_EXPORT void* vmAddressFromLine(GcMethod* m, unsigned line)
{
  GcCode* code = m->code();
  printf("code: %p\n", code);
  GcLineNumberTable* lnt = code->lineNumberTable();
  printf("lnt: %p\n", lnt);

  if (lnt) {
    unsigned last = 0;
    unsigned bottom = 0;
    unsigned top = lnt->length();
    for (unsigned i = bottom; i < top; i++) {
      uint64_t ln = lnt->body()[i];
      if (lineNumberLine(ln) == line)
        return reinterpret_cast<void*>(lineNumberIp(ln));
      else if (lineNumberLine(ln) > line)
        return reinterpret_cast<void*>(last);
      last = lineNumberIp(ln);
    }
  }
  return 0;
}
