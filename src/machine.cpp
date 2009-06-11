/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "jnienv.h"
#include "machine.h"
#include "util.h"
#include "stream.h"
#include "constants.h"
#include "processor.h"

using namespace vm;

namespace {

bool
find(Thread* t, Thread* o)
{
  return (t == o)
    or (t->peer and find(t->peer, o))
    or (t->child and find(t->child, o));
}

void
join(Thread* t, Thread* o)
{
  if (t != o) {
    o->systemThread->join();
    o->state = Thread::JoinedState;
  }
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

void
dispose(Thread* t, Thread* o, bool remove)
{
  if (remove) {
    // debug
    expect(t, find(t->m->rootThread, o));

    unsigned c = count(t->m->rootThread, o);
    Thread* threads[c];
    fill(t->m->rootThread, o, threads);
    // end debug

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

    // debug
    expect(t, not find(t->m->rootThread, o));

    for (unsigned i = 0; i < c; ++i) {
      expect(t, find(t->m->rootThread, threads[i]));
    }
    // end debug
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

  switch (o->state) {
  case Thread::ZombieState:
    join(t, o);
    // fall through

  case Thread::JoinedState:
    dispose(t, o, true);

  default: break;
  }
}

unsigned
footprint(Thread* t)
{
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
  unsigned fixedSizeInWords = ceiling(fixedSize, BytesPerWord);
  unsigned arrayElementSizeInWords
    = ceiling(arrayElementSize, BytesPerWord);

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

void
finalizerTargetUnreachable(Thread* t, Heap::Visitor* v, object* p)
{
  v->visit(&finalizerTarget(t, *p));

  object finalizer = *p;
  *p = finalizerNext(t, finalizer);
  finalizerNext(t, finalizer) = t->m->finalizeQueue;
  t->m->finalizeQueue = finalizer;
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

  if (jreferenceQueue(t, *p)
      and t->m->heap->status(jreferenceQueue(t, *p)) != Heap::Unreachable)
  {
    // queue is reachable - add the reference

    v->visit(&jreferenceQueue(t, *p));

    object q = jreferenceQueue(t, *p);

    set(t, *p, JreferenceJNext, *p);
    if (referenceQueueFront(t, q)) {
      set(t, referenceQueueRear(t, q), JreferenceJNext, *p);
    } else {
      set(t, q, ReferenceQueueFront, *p);
    }
    set(t, q, ReferenceQueueRear, *p);

    jreferenceQueue(t, *p) = 0;
  }

  *p = jreferenceVmNext(t, *p);
}

void
referenceUnreachable(Thread* t, Heap::Visitor* v, object* p)
{
  if (DebugReferences) {
    fprintf(stderr, "reference %p unreachable (target %p)\n",
            *p, jreferenceTarget(t, *p));
  }

  if (jreferenceQueue(t, *p)
      and t->m->heap->status(jreferenceQueue(t, *p)) != Heap::Unreachable)
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

void
postVisit(Thread* t, Heap::Visitor* v)
{
  Machine* m = t->m;
  bool major = m->heap->collectionType() == Heap::MajorCollection;

  assert(t, m->finalizeQueue == 0);

  object firstNewTenuredFinalizer = 0;
  object lastNewTenuredFinalizer = 0;

  for (object* p = &(m->finalizers); *p;) {
    v->visit(p);

    if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
      // target is unreachable - queue it up for finalization
      finalizerTargetUnreachable(t, v, p);
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
      // reference is unreachable
      referenceUnreachable(t, v, p);
    } else if (m->heap->status(jreferenceTarget(t, *p))
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
    for (object* p = &(m->tenuredFinalizers); *p;) {
      v->visit(p);

      if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
        // target is unreachable - queue it up for finalization
        finalizerTargetUnreachable(t, v, p);
      } else {
        // target is reachable
        v->visit(&finalizerTarget(t, *p));
        p = &finalizerNext(t, *p);
      }
    }

    for (object* p = &(m->tenuredWeakReferences); *p;) {
      if (m->heap->status(*p) == Heap::Unreachable) {
        // reference is unreachable
        referenceUnreachable(t, v, p);
      } else if (m->heap->status(jreferenceTarget(t, *p))
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
  t->heapIndex = 0;

  if (t->backupHeap) {
    t->m->heap->free
      (t->backupHeap, t->backupHeapSizeInWords * BytesPerWord);
    t->backupHeap = 0;
    t->backupHeapIndex = 0;
    t->backupHeapSizeInWords = 0;
  }

  for (Thread* c = t->child; c; c = c->peer) {
    postCollect(c);
  }
}

object
makeByteArray(Thread* t, const char* format, va_list a)
{
  const int Size = 256;
  char buffer[Size];
  
  int r = vsnprintf(buffer, Size - 1, format, a);
  expect(t, r >= 0 and r < Size - 1);

  object s = makeByteArray(t, strlen(buffer) + 1);
  memcpy(&byteArrayBody(t, s, 0), buffer, byteArrayLength(t, s));

  return s;
}

object
parseUtf8NonAscii(Thread* t, Stream& s, object bytesSoFar, unsigned byteCount,
                  unsigned sourceIndex, unsigned lastByteRead)
{
  PROTECT(t, bytesSoFar);
  
  unsigned length = byteArrayLength(t, bytesSoFar) - 1;
  object value = makeCharArray(t, length + 1);

  unsigned vi = 0;
  for (; vi < byteCount; ++vi) {
    charArrayBody(t, value, vi) = byteArrayBody(t, bytesSoFar, vi);
  }

  unsigned a = lastByteRead;
  unsigned si = sourceIndex;
  while (true) {
    if (a & 0x80) {
      if (a & 0x20) {
	// 3 bytes
	si += 2;
	assert(t, si < length);
	unsigned b = s.read1();
	unsigned c = s.read1();
	charArrayBody(t, value, vi++)
          = ((a & 0xf) << 12) | ((b & 0x3f) << 6) | (c & 0x3f);
      } else {
	// 2 bytes
	++ si;
	assert(t, si < length);
	unsigned b = s.read1();

	if (a == 0xC0 and b == 0x80) {
	  charArrayBody(t, value, vi++) = 0;
	} else {
	  charArrayBody(t, value, vi++) = ((a & 0x1f) << 6) | (b & 0x3f);
	}
      }
    } else {
      charArrayBody(t, value, vi++) = a;
    }    
    
    if (++si < length) {
      a = s.read1();
    } else {
      break;
    }
  }

  if (vi < length) {
    PROTECT(t, value);
    
    object v = makeCharArray(t, vi + 1);
    memcpy(&charArrayBody(t, v, 0), &charArrayBody(t, value, 0), vi * 2);
    value = v;
  }
  
  charArrayBody(t, value, vi) = 0;
  return value;
}

object
parseUtf8(Thread* t, Stream& s, unsigned length)
{
  object value = makeByteArray(t, length + 1);
  unsigned vi = 0;
  for (unsigned si = 0; si < length; ++si) {
    unsigned a = s.read1();
    if (a & 0x80) {
      if (a & 0x20) {
	// 3 bytes
        return parseUtf8NonAscii(t, s, value, vi, si, a);
      } else {
	// 2 bytes
	unsigned b = s.read1();

	if (a == 0xC0 and b == 0x80) {
          ++ si;
          assert(t, si < length);
	  byteArrayBody(t, value, vi++) = 0;
	} else {
          return parseUtf8NonAscii(t, s, value, vi, si, a);
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
  
  byteArrayBody(t, value, vi) = 0;
  return value;
}

void
removeByteArray(Thread* t, object o)
{
  hashMapRemove(t, t->m->byteArrayMap, o, byteArrayHash, objectEqual);
}

object
internByteArray(Thread* t, object array)
{
  PROTECT(t, array);

  object n = hashMapFindNode
    (t, t->m->byteArrayMap, array, byteArrayHash, byteArrayEqual);
  if (n) {
    return jreferenceTarget(t, tripleFirst(t, n));
  } else {
    hashMapInsert(t, t->m->byteArrayMap, array, 0, byteArrayHash);
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
    singletonValue(t, pool, i) = s.read4();
  } return 1;
    
  case CONSTANT_Long:
  case CONSTANT_Double: {
    uint64_t v = s.read8();
    memcpy(&singletonValue(t, pool, i), &v, 8);
  } return 2;

  case CONSTANT_Utf8: {
    if (singletonObject(t, pool, i) == 0) {
      object value = parseUtf8(t, s, s.read2());
      if (objectClass(t, value)
          == arrayBody(t, t->m->types, Machine::ByteArrayType))
      {
        value = internByteArray(t, value);
      }
      set(t, pool, SingletonBody + (i * BytesPerWord), value);
    }
  } return 1;

  case CONSTANT_Class: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = singletonObject(t, pool, si);
      set(t, pool, SingletonBody + (i * BytesPerWord), value);
    }
  } return 1;

  case CONSTANT_String: {
    if (singletonObject(t, pool, i) == 0) {
      unsigned si = s.read2() - 1;
      parsePoolEntry(t, s, index, pool, si);
        
      object value = singletonObject(t, pool, si);
      value = makeString
        (t, value, 0, cast<uintptr_t>(value, BytesPerWord) - 1, 0);
      value = intern(t, value);
      set(t, pool, SingletonBody + (i * BytesPerWord), value);
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
        
      object class_ = singletonObject(t, pool, ci);
      object nameAndType = singletonObject(t, pool, nti);
      object value = makeReference
          (t, class_, pairFirst(t, nameAndType), pairSecond(t, nameAndType));
      set(t, pool, SingletonBody + (i * BytesPerWord), value);
    }
  } return 1;

  default: abort(t);
  }
}

object
parsePool(Thread* t, Stream& s)
{
  unsigned count = s.read2() - 1;

  object pool = makeSingletonOfSize(t, count);
  PROTECT(t, pool);

  if (count) {
    uint32_t* index = static_cast<uint32_t*>(t->m->heap->allocate(count * 4));

    for (unsigned i = 0; i < count; ++i) {
      index[i] = s.position();

      switch (s.read1()) {
      case CONSTANT_Class:
      case CONSTANT_String:
        singletonMarkObject(t, pool, i);
        s.skip(2);
        break;

      case CONSTANT_Integer:
      case CONSTANT_Float:
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
      case CONSTANT_Double:
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

    t->m->heap->free(index, count * 4);

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
    object name = singletonObject(t, pool, s.read2() - 1);
    PROTECT(t, name);

    object interface = resolveClass(t, name);
    PROTECT(t, interface);

    hashMapInsertMaybe(t, map, name, interface, byteArrayHash, byteArrayEqual);

    addInterfaces(t, interface, map);
  }

  object interfaceTable = 0;
  if (hashMapSize(t, map)) {
    unsigned length = hashMapSize(t, map) ;
    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      length *= 2;
    }
    interfaceTable = makeArray(t, length);
    PROTECT(t, interfaceTable);

    unsigned i = 0;
    for (HashMapIterator it(t, map); it.hasMore();) {
      object interface = resolveClass(t, tripleFirst(t, it.next()));
      if (UNLIKELY(t->exception)) return;

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
    unsigned staticOffset = BytesPerWord * 2;
    unsigned staticCount = 0;
  
    object fieldTable = makeArray(t, count);
    PROTECT(t, fieldTable);

    object staticValueTable = makeIntArray(t, count);
    PROTECT(t, staticValueTable);

    uint8_t staticTypes[count];

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      unsigned value = 0;

      unsigned code = fieldCode
        (t, byteArrayBody(t, singletonObject(t, pool, spec - 1), 0));

      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object name = singletonObject(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (strcmp(reinterpret_cast<const int8_t*>("ConstantValue"),
                   &byteArrayBody(t, name, 0)) == 0)
        {
          value = s.read2();
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
         singletonObject(t, pool, name - 1),
         singletonObject(t, pool, spec - 1),
         class_);

      if (flags & ACC_STATIC) {
        unsigned size = fieldSize(t, code);
        unsigned excess = (staticOffset % size) % BytesPerWord;
        if (excess) {
          staticOffset += BytesPerWord - excess;
        }

        fieldOffset(t, field) = staticOffset;

        staticOffset += size;

        intArrayBody(t, staticValueTable, staticCount) = value;

        staticTypes[staticCount++] = code;
      } else {
        if (flags & ACC_FINAL) {
          classFlags(t, class_) |= HasFinalMemberFlag;
        }

        unsigned excess = (memberOffset % fieldSize(t, code)) % BytesPerWord;
        if (excess) {
          memberOffset += BytesPerWord - excess;
        }

        fieldOffset(t, field) = memberOffset;
        memberOffset += fieldSize(t, code);
      }

      set(t, fieldTable, ArrayBody + (i * BytesPerWord), field);
    }

    set(t, class_, ClassFieldTable, fieldTable);

    if (staticCount) {
      unsigned footprint = ceiling(staticOffset - (BytesPerWord * 2),
                                   BytesPerWord);
      object staticTable = makeSingletonOfSize(t, footprint);

      uint8_t* body = reinterpret_cast<uint8_t*>
        (&singletonBody(t, staticTable, 0));

      for (unsigned i = 0, offset = 0; i < staticCount; ++i) {
        unsigned size = fieldSize(t, staticTypes[i]);
        unsigned excess = offset % size;
        if (excess) {
          offset += BytesPerWord - excess;
        }

        unsigned value = intArrayBody(t, staticValueTable, i);
        if (value) {
          switch (staticTypes[i]) {
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

        if (staticTypes[i] == ObjectField) {
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
      (t, ceiling(classFixedSize(t, class_), 32 * BytesPerWord));
    intArrayBody(t, mask, 0) = 1;

    object superMask = 0;
    if (classSuper(t, class_)) {
      superMask = classObjectMask(t, classSuper(t, class_));
      if (superMask) {
        memcpy(&intArrayBody(t, mask, 0),
               &intArrayBody(t, superMask, 0),
               ceiling(classFixedSize(t, classSuper(t, class_)),
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

object
parseCode(Thread* t, Stream& s, object pool)
{
  PROTECT(t, pool);

  unsigned maxStack = s.read2();
  unsigned maxLocals = s.read2();
  unsigned length = s.read4();

  object code = makeCode(t, pool, 0, 0, maxStack, maxLocals, length);
  s.read(&codeBody(t, code, 0), length);
  PROTECT(t, code);

  unsigned ehtLength = s.read2();
  if (ehtLength) {
    object eht = makeExceptionHandlerTable(t, ehtLength);
    for (unsigned i = 0; i < ehtLength; ++i) {
      ExceptionHandler* eh = exceptionHandlerTableBody(t, eht, i);
      exceptionHandlerStart(eh) = s.read2();
      exceptionHandlerEnd(eh) = s.read2();
      exceptionHandlerIp(eh) = s.read2();
      exceptionHandlerCatchType(eh) = s.read2();
    }

    set(t, code, CodeExceptionHandlerTable, eht);
  }

  unsigned attributeCount = s.read2();
  for (unsigned j = 0; j < attributeCount; ++j) {
    object name = singletonObject(t, pool, s.read2() - 1);
    unsigned length = s.read4();

    if (strcmp(reinterpret_cast<const int8_t*>("LineNumberTable"),
               &byteArrayBody(t, name, 0)) == 0)
    {
      unsigned lntLength = s.read2();
      object lnt = makeLineNumberTable(t, lntLength);
      for (unsigned i = 0; i < lntLength; ++i) {
        LineNumber* ln = lineNumberTableBody(t, lnt, i);
        lineNumberIp(ln) = s.read2();
        lineNumberLine(ln) = s.read2();
      }

      set(t, code, CodeLineNumberTable, lnt);
    } else {
      s.skip(length);
    }
  }

  return code;
}

void
scanMethodSpec(Thread* t, const char* s, unsigned* parameterCount,
               unsigned* returnCode)
{
  unsigned count = 0;
  MethodSpecIterator it(t, s);
  for (; it.hasNext(); it.next()) {
    ++ count;
  }

  *parameterCount = count;
  *returnCode = fieldCode(t, *it.returnSpec());
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
    object itable = classInterfaceTable(t, class_);
    if (itable) {
      PROTECT(t, itable);
      for (unsigned i = 0; i < arrayLength(t, itable); ++i) {
        object vtable = classVirtualTable(t, arrayBody(t, itable, i));
        if (vtable) {
          PROTECT(t, vtable);
          for (unsigned j = 0; j < arrayLength(t, vtable); ++j) {
            object method = arrayBody(t, vtable, j);
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
                 virtualCount++,
                 0,
                 methodName(t, method),
                 methodSpec(t, method),
                 class_,
                 0,
                 0);
              hashMapInsert(t, virtualMap, method, method, methodHash);
            }
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
    object methodTable = makeArray(t, count);
    PROTECT(t, methodTable);

    for (unsigned i = 0; i < count; ++i) {
      unsigned flags = s.read2();
      unsigned name = s.read2();
      unsigned spec = s.read2();

      object code = 0;
      unsigned attributeCount = s.read2();
      for (unsigned j = 0; j < attributeCount; ++j) {
        object name = singletonObject(t, pool, s.read2() - 1);
        unsigned length = s.read4();

        if (strcmp(reinterpret_cast<const int8_t*>("Code"),
                   &byteArrayBody(t, name, 0)) == 0)
        {
          code = parseCode(t, s, pool);
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
      } else {
        methodOffset(t, method) = i;

        if (strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                   &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          methodVmFlags(t, method) |= ClassInitFlag;
          classVmFlags(t, class_) |= NeedInitFlag;
        } else if (strcmp(reinterpret_cast<const int8_t*>("<init>"), 
                          &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          methodVmFlags(t, method) |= ConstructorFlag;
        }
      }

      set(t, methodTable, ArrayBody + (i * BytesPerWord), method);
    }

    set(t, class_, ClassMethodTable, methodTable);
  }

  bool populateInterfaceVtables = false;

  if (declaredVirtualCount == 0
      and (classFlags(t, class_) & ACC_INTERFACE) == 0)
  {
    if (classSuper(t, class_)) {
      // inherit virtual table from superclass
      set(t, class_, ClassVirtualTable, superVirtualTable);
      
      if (classInterfaceTable(t, classSuper(t, class_))
          and arrayLength(t, classInterfaceTable(t, class_))
          == arrayLength(t, classInterfaceTable(t, classSuper(t, class_))))
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

    assert(t, arrayLength(t, vtable) == i);

    set(t, class_, ClassVirtualTable, vtable);

    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
      populateInterfaceVtables = true;
    }
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

            // note that method may be null in the case of an abstract
            // class
              
            set(t, vtable, ArrayBody + (j * BytesPerWord), method);        
          }
        }
      }
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

  if (classFlags(t, newClass) & ACC_INTERFACE) {
    object virtualTable = classVirtualTable(t, newClass);
    if (virtualTable) {
      for (unsigned i = 0; i < arrayLength(t, virtualTable); ++i) {
        if (methodClass(t, arrayBody(t, virtualTable, i)) == oldClass) {
          set(t, arrayBody(t, virtualTable, i), MethodClass, newClass);
        }
      }
    }
  } else {
    object methodTable = classMethodTable(t, newClass);
    if (methodTable) {
      for (unsigned i = 0; i < arrayLength(t, methodTable); ++i) {
        set(t, arrayBody(t, methodTable, i), MethodClass, newClass);
      }
    }
  }
}

void
updateBootstrapClass(Thread* t, object bootstrapClass, object class_)
{
  expect(t, bootstrapClass != class_);

  // verify that the classes have the same layout
  expect(t, classSuper(t, bootstrapClass) == classSuper(t, class_));

  expect(t, bootstrapClass == arrayBody(t, t->m->types, Machine::ClassType)
         or classFixedSize(t, bootstrapClass) >= classFixedSize(t, class_));

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

  updateClassTables(t, bootstrapClass, class_);
}

object
makeArrayClass(Thread* t, unsigned dimensions, object spec,
               object elementClass)
{
  // todo: arrays should implement Cloneable and Serializable
  object vtable = classVirtualTable
    (t, arrayBody(t, t->m->types, Machine::JobjectType));

  object c = t->m->processor->makeClass
    (t,
     0,
     0,
     dimensions,
     2 * BytesPerWord,
     BytesPerWord,
     classObjectMask(t, arrayBody(t, t->m->types, Machine::ArrayType)),
     spec,
     arrayBody(t, t->m->types, Machine::JobjectType),
     0,
     vtable,
     0,
     0,
     elementClass,
     t->m->loader,
     arrayLength(t, vtable));

  t->m->processor->initVtable(t, c);

  return c;
}

object
makeArrayClass(Thread* t, object spec)
{
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
      elementSpec = makeByteArray(t, 3);
      byteArrayBody(t, elementSpec, 0) = '[';
      byteArrayBody(t, elementSpec, 1) = c;
      byteArrayBody(t, elementSpec, 2) = 0;
      -- dimensions;
    } else {
      abort(t);
    }
  }

  object elementClass = hashMapFind
    (t, t->m->bootstrapClassMap, elementSpec, byteArrayHash, byteArrayEqual);

  if (elementClass == 0) {
    elementClass = resolveClass(t, elementSpec);
    if (UNLIKELY(t->exception)) return 0;
  }

  return makeArrayClass(t, dimensions, spec, elementClass);
}

void
removeMonitor(Thread* t, object o)
{
  expect(t, t->state == Thread::ExclusiveState
         or t->state == Thread::ExitState);

  unsigned hash;
  if (DebugMonitors) {
    hash = objectHash(t, o);
  }

  object p = hashMapRemove(t, t->m->monitorMap, o, objectHash, objectEqual);

  expect(t, p);

  if (DebugMonitors) {
    fprintf(stderr, "dispose monitor %p for object %x\n",
            static_cast<System::Monitor*>(pointerValue(t, p)),
            hash);
  }

  static_cast<System::Monitor*>(pointerValue(t, p))->dispose();
}

void
removeString(Thread* t, object o)
{
  hashMapRemove(t, t->m->stringMap, o, stringHash, objectEqual);
}

void
bootClass(Thread* t, Machine::Type type, int superType, uint32_t objectMask,
          unsigned fixedSize, unsigned arrayElementSize, unsigned vtableLength)
{
  object super = (superType >= 0 ? arrayBody(t, t->m->types, superType) : 0);

  object mask;
  if (objectMask) {
    if (super
        and classObjectMask(t, super)
        and intArrayBody(t, classObjectMask(t, super), 0)
        == static_cast<int32_t>(objectMask))
    {
      mask = classObjectMask(t, arrayBody(t, t->m->types, superType));
    } else {
      mask = makeIntArray(t, 1);
      intArrayBody(t, mask, 0) = objectMask;
    }
  } else {
    mask = 0;
  }

  super = (superType >= 0 ? arrayBody(t, t->m->types, superType) : 0);

  object class_ = t->m->processor->makeClass
    (t, 0, BootstrapFlag, 0, fixedSize, arrayElementSize, mask, 0, super, 0, 0,
     0, 0, 0, t->m->loader, vtableLength);

  set(t, t->m->types, ArrayBody + (type * BytesPerWord), class_);
}

void
bootJavaClass(Thread* t, Machine::Type type, int superType, const char* name,
              int vtableLength, object bootMethod)
{
  PROTECT(t, bootMethod);

  object n = makeByteArray(t, name);
  object class_ = arrayBody(t, t->m->types, type);

  set(t, class_, ClassName, n);

  object vtable;
  if (vtableLength >= 0) {
    PROTECT(t, class_);

    vtable = makeArray(t, vtableLength);
    for (int i = 0; i < vtableLength; ++ i) {
      arrayBody(t, vtable, i) = bootMethod;
    }
  } else {
    vtable = classVirtualTable(t, arrayBody(t, t->m->types, superType));
  }

  set(t, class_, ClassVirtualTable, vtable);

  t->m->processor->initVtable(t, class_);

  hashMapInsert(t, t->m->bootstrapClassMap, n, class_, byteArrayHash);
}

void
boot(Thread* t)
{
  Machine* m = t->m;

  m->unsafe = true;

  m->loader = allocate(t, FixedSizeOfSystemClassLoader, true);

  m->types = allocate(t, pad((TypeCount + 2) * BytesPerWord), true);
  arrayLength(t, m->types) = TypeCount;

#include "type-initializations.cpp"

  object arrayClass = arrayBody(t, m->types, Machine::ArrayType);
  set(t, m->types, 0, arrayClass);

  object loaderClass = arrayBody
    (t, m->types, Machine::SystemClassLoaderType);
  set(t, m->loader, 0, loaderClass);

  object objectClass = arrayBody(t, m->types, Machine::JobjectType);

  object classClass = arrayBody(t, m->types, Machine::ClassType);
  set(t, classClass, 0, classClass);
  set(t, classClass, ClassSuper, objectClass);

  object intArrayClass = arrayBody(t, m->types, Machine::IntArrayType);
  set(t, intArrayClass, 0, classClass);
  set(t, intArrayClass, ClassSuper, objectClass);

  m->unsafe = false;

  classFlags(t, arrayBody(t, m->types, Machine::SingletonType))
    |= SingletonFlag;

  classFlags(t, arrayBody(t, m->types, Machine::ContinuationType))
    |= ContinuationFlag;

  classVmFlags(t, arrayBody(t, m->types, Machine::JreferenceType))
    |= ReferenceFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::WeakReferenceType))
    |= ReferenceFlag | WeakReferenceFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::PhantomReferenceType))
    |= ReferenceFlag | WeakReferenceFlag;

  classVmFlags(t, arrayBody(t, m->types, Machine::JbooleanType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JbyteType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JcharType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JshortType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JintType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JlongType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JfloatType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JdoubleType))
    |= PrimitiveFlag;
  classVmFlags(t, arrayBody(t, m->types, Machine::JvoidType))
    |= PrimitiveFlag;

  m->bootstrapClassMap = makeHashMap(t, 0, 0);

  m->classMap = makeHashMap(t, 0, 0);

  m->stringMap = makeWeakHashMap(t, 0, 0);

  m->processor->boot(t, 0);

  { object bootCode = makeCode(t, 0, 0, 0, 0, 0, 1);
    codeBody(t, bootCode, 0) = impdep1;
    object bootMethod = makeMethod
      (t, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, bootCode, 0);
    PROTECT(t, bootMethod);

#include "type-java-initializations.cpp"
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

    object o = static_cast<object>(m->heap->follow(mask(p)));

    unsigned n = baseSize(t, o, static_cast<object>
                          (m->heap->follow(objectClass(t, o))));

    if (objectExtended(t, o)) {
      ++ n;
    }

    return n;
  }

  virtual unsigned copiedSizeInWords(void* p) {
    Thread* t = m->rootThread;

    object o = static_cast<object>(m->heap->follow(mask(p)));
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

    object src = static_cast<object>(m->heap->follow(mask(srcp)));
    assert(t, not objectFixed(t, src));

    object class_ = static_cast<object>
      (m->heap->follow(objectClass(t, src)));

    unsigned base = baseSize(t, src, class_);
    unsigned n = extendedSize(t, src, base);

    object dst = static_cast<object>(dstp);

    memcpy(dst, src, n * BytesPerWord);

    if (hashTaken(t, src)) {
      cast<uintptr_t>(dst, 0) &= PointerMask;
      cast<uintptr_t>(dst, 0) |= ExtendedMark;
      extendedWord(t, dst, base) = takeHash(t, src);
    }
  }

  virtual void walk(void* p, Heap::Walker* w) {
    object o = static_cast<object>(m->heap->follow(mask(p)));
    ::walk(m->rootThread, w, o, 0);
  }

  void dispose() {
    m->heap->free(this, sizeof(*this));
  }
    
 private:
  Machine* m;
};

} // namespace

namespace vm {

Machine::Machine(System* system, Heap* heap, Finder* finder,
                 Processor* processor, const char** properties,
                 unsigned propertyCount):
  vtable(&javaVMVTable),
  system(system),
  heapClient(new (heap->allocate(sizeof(HeapClient)))
             HeapClient(this)),
  heap(heap),
  finder(finder),
  processor(processor),
  rootThread(0),
  exclusive(0),
  jniReferences(0),
  properties(properties),
  propertyCount(propertyCount),
  activeCount(0),
  liveCount(0),
  fixedFootprint(0),
  localThread(0),
  stateLock(0),
  heapLock(0),
  classLock(0),
  referenceLock(0),
  libraries(0),
  loader(0),
  classMap(0),
  bootstrapClassMap(0),
  monitorMap(0),
  stringMap(0),
  byteArrayMap(0),
  types(0),
  jniMethodTable(0),
  finalizers(0),
  tenuredFinalizers(0),
  finalizeQueue(0),
  weakReferences(0),
  tenuredWeakReferences(0),
  unsafe(false),
  triedBuiltinOnLoad(false),
  heapPoolIndex(0)
{
  heap->setClient(heapClient);

  populateJNITables(&javaVMVTable, &jniEnvVTable);

  if (not system->success(system->make(&localThread)) or
      not system->success(system->make(&stateLock)) or
      not system->success(system->make(&heapLock)) or
      not system->success(system->make(&classLock)) or
      not system->success(system->make(&referenceLock)) or
      not system->success
      (system->load(&libraries, findProperty(this, "avian.bootstrap"), false)))
  {
    system->abort();
  }
}

void
Machine::dispose()
{
  localThread->dispose();
  stateLock->dispose();
  heapLock->dispose();
  classLock->dispose();
  referenceLock->dispose();

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

  heap->free(properties, sizeof(const char*) * propertyCount);

  static_cast<HeapClient*>(heapClient)->dispose();

  heap->free(this, sizeof(*this));
}

Thread::Thread(Machine* m, object javaThread, Thread* parent):
  vtable(&(m->jniEnvVTable)),
  m(m),
  parent(parent),
  peer((parent ? parent->child : 0)),
  child(0),
  state(NoState),
  criticalLevel(0),
  systemThread(0),
  javaThread(javaThread),
  exception(0),
  heapIndex(0),
  heapOffset(0),
  protector(0),
  runnable(this),
  defaultHeap(static_cast<uintptr_t*>
              (m->heap->allocate(ThreadHeapSizeInBytes))),
  heap(defaultHeap),
  backupHeap(0),
  backupHeapIndex(0),
  backupHeapSizeInWords(0),
  tracing(false)
#ifdef VM_STRESS
  , stress(false)
#endif // VM_STRESS
{ }

void
Thread::init()
{
  memset(defaultHeap, 0, ThreadHeapSizeInBytes);

  if (parent == 0) {
    assert(this, m->rootThread == 0);
    assert(this, javaThread == 0);

    m->rootThread = this;
    m->unsafe = true;

    if (not m->system->success(m->system->attach(&runnable))) {
      abort(this);
    }

    BootImage* image = 0;
    const char* imageFunctionName = findProperty(m, "avian.bootimage");
    if (imageFunctionName) {
      void* p = m->libraries->resolve(imageFunctionName);
      if (p) {
        BootImage* (*function)(unsigned*);
        memcpy(&function, &p, BytesPerWord);

        unsigned size;
        image = function(&size);
      }
    }

    m->unsafe = false;

    if (image) {
      m->processor->boot(this, image);
    } else {
      boot(this);
    }

    m->byteArrayMap = makeWeakHashMap(this, 0, 0);
    m->monitorMap = makeWeakHashMap(this, 0, 0);

    m->jniMethodTable = makeVector(this, 0, 0);

    m->localThread->set(this);
  } else {
    peer = parent->child;
    parent->child = this;
  }

  if (javaThread) {
    threadPeer(this, javaThread) = reinterpret_cast<jlong>(this);
  } else {
    const unsigned NewState = 0;
    const unsigned NormalPriority = 5;

    this->javaThread = makeThread
      (this, reinterpret_cast<int64_t>(this), 0, 0, NewState, NormalPriority,
       0, 0, 0, m->loader, 0, 0, 0);
  }
}

void
Thread::exit()
{
  if (state != Thread::ExitState and
      state != Thread::ZombieState)
  {
    enter(this, Thread::ExclusiveState);

    if (m->liveCount == 1) {
      vm::exit(this);
    } else {
      enter(this, Thread::ZombieState);
    }
  }
}

void
Thread::dispose()
{
  if (systemThread) {
    systemThread->dispose();
  }

  m->heap->free(defaultHeap, ThreadHeapSizeInBytes);

  m->processor->dispose(this);
}

void
exit(Thread* t)
{
  enter(t, Thread::ExitState);

  joinAll(t, t->m->rootThread);

  for (object* p = &(t->m->finalizers); *p;) {
    object f = *p;
    *p = finalizerNext(t, *p);

    void (*function)(Thread*, object);
    memcpy(&function, &finalizerFinalize(t, f), BytesPerWord);
    function(t, finalizerTarget(t, f));
  }

  for (object* p = &(t->m->tenuredFinalizers); *p;) {
    object f = *p;
    *p = finalizerNext(t, *p);

    void (*function)(Thread*, object);
    memcpy(&function, &finalizerFinalize(t, f), BytesPerWord);
    function(t, finalizerTarget(t, f));
  }

  disposeAll(t, t->m->rootThread);
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

  ACQUIRE_RAW(t, t->m->stateLock);

  switch (s) {
  case Thread::ExclusiveState: {
    while (t->m->exclusive) {
      // another thread got here first.
      ENTER(t, Thread::IdleState);
    }

    switch (t->state) {
    case Thread::ActiveState: break;

    case Thread::IdleState: {
      ++ t->m->activeCount;
    } break;

    default: abort(t);
    }

    t->state = Thread::ExclusiveState;
    t->m->exclusive = t;
      
    while (t->m->activeCount > 1) {
      t->m->stateLock->wait(t->systemThread, 0);
    }
  } break;

  case Thread::IdleState:
  case Thread::ZombieState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->m->exclusive == t);
      t->m->exclusive = 0;
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assert(t, t->m->activeCount > 0);
    -- t->m->activeCount;

    if (s == Thread::ZombieState) {
      assert(t, t->m->liveCount > 0);
      -- t->m->liveCount;
    }
    t->state = s;

    t->m->stateLock->notifyAll(t->systemThread);
  } break;

  case Thread::ActiveState: {
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

      ++ t->m->activeCount;
      if (t->state == Thread::NoState) {
        ++ t->m->liveCount;
      }
      t->state = s;
    } break;

    default: abort(t);
    }
  } break;

  case Thread::ExitState: {
    switch (t->state) {
    case Thread::ExclusiveState: {
      assert(t, t->m->exclusive == t);
      t->m->exclusive = 0;

      t->m->stateLock->notifyAll(t->systemThread);
    } break;

    case Thread::ActiveState: break;

    default: abort(t);
    }

    assert(t, t->m->activeCount > 0);
    -- t->m->activeCount;

    t->state = s;

    while (t->m->liveCount > 1) {
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
     ceiling(sizeInBytes, BytesPerWord) > ThreadHeapSizeInWords ?
     Machine::FixedAllocation : Machine::MovableAllocation,
     sizeInBytes, objectMask);
}

object
allocate3(Thread* t, Allocator* allocator, Machine::AllocationType type,
          unsigned sizeInBytes, bool objectMask)
{
  if (t->backupHeap) {
    expect(t,  t->backupHeapIndex + ceiling(sizeInBytes, BytesPerWord)
           <= t->backupHeapSizeInWords);
    
    object o = reinterpret_cast<object>(t->backupHeap + t->backupHeapIndex);
    t->backupHeapIndex += ceiling(sizeInBytes, BytesPerWord);
    cast<object>(o, 0) = 0;
    return o;
  } else if (t->tracing) {
    expect(t, t->heapIndex + ceiling(sizeInBytes, BytesPerWord)
           <= ThreadHeapSizeInWords);
    return allocateSmall(t, sizeInBytes);
  }

  ACQUIRE_RAW(t, t->m->stateLock);

  while (t->m->exclusive and t->m->exclusive != t) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    ENTER(t, Thread::IdleState);
  }
  
  switch (type) {
  case Machine::MovableAllocation:
    if (t->heapIndex + ceiling(sizeInBytes, BytesPerWord)
        > ThreadHeapSizeInWords)
    {
      t->heap = 0;
      if (t->m->heapPoolIndex < ThreadHeapPoolSize) {
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
    if (t->m->fixedFootprint + sizeInBytes > FixedFootprintThresholdInBytes) {
      t->heap = 0;
    }
    break;

  case Machine::ImmortalAllocation:
    break;
  }

  if (t->heap == 0) {
//     fprintf(stderr, "gc");
//     vmPrintTrace(t);
    collect(t, Heap::MinorCollection);
  }
  
  switch (type) {
  case Machine::MovableAllocation: {
    return allocateSmall(t, sizeInBytes);
  }

  case Machine::FixedAllocation: {
    unsigned total;
    object o = static_cast<object>
      (t->m->heap->allocateFixed
       (allocator, ceiling(sizeInBytes, BytesPerWord), objectMask, &total));

    memset(o, 0, sizeInBytes);

    cast<uintptr_t>(o, 0) = FixedMark;

    t->m->fixedFootprint += total;

    return o;
  }

  case Machine::ImmortalAllocation: {
    unsigned total;
    object o = static_cast<object>
      (t->m->heap->allocateImmortalFixed
       (allocator, ceiling(sizeInBytes, BytesPerWord), objectMask, &total));

    memset(o, 0, sizeInBytes);

    cast<uintptr_t>(o, 0) = FixedMark;

    return o;
  }

  default: abort(t);
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

  return makeString(t, s, 0, byteArrayLength(t, s) - 1, 0);
}

void
stringChars(Thread* t, object string, char* chars)
{
  object data = stringData(t, string);
  if (objectClass(t, data)
      == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    memcpy(chars,
           &byteArrayBody(t, data, stringOffset(t, string)),
           stringLength(t, string));
  } else {
    for (unsigned i = 0; i < stringLength(t, string); ++i) {
      chars[i] = charArrayBody(t, data, stringOffset(t, string) + i);
    }
  }
  chars[stringLength(t, string)] = 0;
}

void
stringChars(Thread* t, object string, uint16_t* chars)
{
  object data = stringData(t, string);
  if (objectClass(t, data)
      == arrayBody(t, t->m->types, Machine::ByteArrayType))
  {
    for (unsigned i = 0; i < stringLength(t, string); ++i) {
      chars[i] = byteArrayBody(t, data, stringOffset(t, string) + i);
    }
  } else {
    memcpy(chars,
           &charArrayBody(t, data, stringOffset(t, string)),
           stringLength(t, string) * sizeof(uint16_t));
  }
  chars[stringLength(t, string)] = 0;
}

bool
isAssignableFrom(Thread* t, object a, object b)
{
  if (a == b) return true;

  if (classFlags(t, a) & ACC_INTERFACE) {
    if (classVmFlags(t, b) & BootstrapFlag) {
      resolveClass(t, className(t, b));
      if (UNLIKELY(t->exception)) {
        t->exception = 0;
        return false;
      }
    }

    for (; b; b = classSuper(t, b)) {
      object itable = classInterfaceTable(t, b);
      if (itable) {
        for (unsigned i = 0; i < arrayLength(t, itable); i += 2) {
          if (arrayBody(t, itable, i) == a) {
            return true;
          }
        }
      }
    }
  } else if (classArrayDimensions(t, a)) {
    if (classArrayDimensions(t, b)) {
      return isAssignableFrom
        (t, classStaticTable(t, a), classStaticTable(t, b));
    }
  } else {
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
  for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, class_)); ++i) {
    object o = arrayBody(t, classMethodTable(t, class_), i);

    if (strcmp(reinterpret_cast<const int8_t*>("<clinit>"),
               &byteArrayBody(t, methodName(t, o), 0)) == 0)
    {
      return o;
    }               
  }
  abort(t);
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
findLoadedClass(Thread* t, object spec)
{
  PROTECT(t, spec);
  ACQUIRE(t, t->m->classLock);

  return hashMapFind(t, t->m->classMap, spec, byteArrayHash, byteArrayEqual);
}

object
parseClass(Thread* t, const uint8_t* data, unsigned size)
{
  class Client : public Stream::Client {
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
  s.read2(); // minor version
  s.read2(); // major version

  object pool = parsePool(t, s);
  PROTECT(t, pool);

  unsigned flags = s.read2();
  unsigned name = s.read2();

  object class_ = makeClass(t,
                            flags,
                            0, // VM flags
                            0, // array dimensions
                            0, // fixed size
                            0, // array size
                            0, // object mask
                            singletonObject(t, pool, name - 1),
                            0, // super
                            0, // interfaces
                            0, // vtable
                            0, // fields
                            0, // methods
                            0, // static table
                            t->m->loader,
                            0);// vtable length
  PROTECT(t, class_);
  
  unsigned super = s.read2();
  if (super) {
    object sc = resolveClass(t, singletonObject(t, pool, super - 1));
    if (UNLIKELY(t->exception)) return 0;

    set(t, class_, ClassSuper, sc);

    classVmFlags(t, class_)
      |= (classVmFlags(t, sc) & (ReferenceFlag | WeakReferenceFlag));
  }
  
  parseInterfaceTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseFieldTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseMethodTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  object vtable = classVirtualTable(t, class_);
  unsigned vtableLength = (vtable ? arrayLength(t, vtable) : 0);

  object real = t->m->processor->makeClass
    (t,
     classFlags(t, class_),
     classVmFlags(t, class_),
     classArrayDimensions(t, class_),
     classFixedSize(t, class_),
     classArrayElementSize(t, class_),
     classObjectMask(t, class_),
     className(t, class_),
     classSuper(t, class_),
     classInterfaceTable(t, class_),
     classVirtualTable(t, class_),
     classFieldTable(t, class_),
     classMethodTable(t, class_),
     classStaticTable(t, class_),
     classLoader(t, class_),
     vtableLength);

  PROTECT(t, real);

  t->m->processor->initVtable(t, real);

  updateClassTables(t, real, class_);

  return real;
}

object
resolveClass(Thread* t, object spec)
{
  PROTECT(t, spec);
  ACQUIRE(t, t->m->classLock);

  object class_ = hashMapFind
    (t, t->m->classMap, spec, byteArrayHash, byteArrayEqual);

  if (class_ == 0) {
    if (byteArrayBody(t, spec, 0) == '[') {
      class_ = hashMapFind
        (t, t->m->bootstrapClassMap, spec, byteArrayHash, byteArrayEqual);

      if (class_) {
        set(t, class_, ClassVirtualTable,
            classVirtualTable
            (t, arrayBody(t, t->m->types, Machine::JobjectType)));
      } else {
        class_ = makeArrayClass(t, spec);
      }
    } else {
      char file[byteArrayLength(t, spec) + 6];
      memcpy(file, &byteArrayBody(t, spec, 0), byteArrayLength(t, spec) - 1);
      memcpy(file + byteArrayLength(t, spec) - 1, ".class", 7);

      System::Region* region = t->m->finder->find(file);

      if (region) {
        if (Verbose) {
          fprintf(stderr, "parsing %s\n", &byteArrayBody(t, spec, 0));
        }

        // parse class file
        class_ = parseClass(t, region->start(), region->length());
        region->dispose();

        if (LIKELY(t->exception == 0)) {
          if (Verbose) {
            fprintf(stderr, "done parsing %s: %p\n",
                    &byteArrayBody(t, spec, 0),
                    class_);
          }

          object bootstrapClass = hashMapFind
            (t, t->m->bootstrapClassMap, spec, byteArrayHash, byteArrayEqual);

          if (bootstrapClass) {
            PROTECT(t, bootstrapClass);

            updateBootstrapClass(t, bootstrapClass, class_);
            class_ = bootstrapClass;
          }
        }
      }
    }

    if (class_) {
      PROTECT(t, class_);

      hashMapInsert(t, t->m->classMap, spec, class_, byteArrayHash);
    } else if (t->exception == 0) {
      object message = makeString(t, "%s", &byteArrayBody(t, spec, 0));
      t->exception = makeClassNotFoundException(t, message);
    }
  }

  return class_;
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

  if (t->exception == 0 and method == 0) {
    object message = makeString
      (t, "%s %s not found in %s", methodName, methodSpec, className);

    t->exception = makeNoSuchMethodError(t, message);
    return 0;
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
 
  object field = 0;
  for (; class_ != 0 and field == 0; class_ = classSuper(t, class_)) {
    field = findFieldInClass(t, class_, name, spec);
  }

  if (t->exception == 0 and field == 0) {
    object message = makeString
      (t, "%s %s not found in %s", fieldName, fieldSpec, className);

    t->exception = makeNoSuchFieldError(t, message);
    return 0;
  } else {
    return field;
  }
}

object
resolveObjectArrayClass(Thread* t, object elementSpec)
{
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

  return resolveClass(t, spec);
}

object
makeObjectArray(Thread* t, object elementClass, unsigned count)
{
  object arrayClass = resolveObjectArrayClass(t, className(t, elementClass));
  PROTECT(t, arrayClass);

  object array = makeArray(t, count);
  setObjectClass(t, array, arrayClass);

  return array;
}

object
findInTable(Thread* t, object table, object name, object spec,
            object& (*getName)(Thread*, object),
            object& (*getSpec)(Thread*, object))
{
  if (table) {
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object o = arrayBody(t, table, i);      
      if (strcmp(&byteArrayBody(t, getName(t, o), 0),
                 &byteArrayBody(t, name, 0)) == 0 and
          strcmp(&byteArrayBody(t, getSpec(t, o), 0),
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

object
findInHierarchy(Thread* t, object class_, object name, object spec,
                object (*find)(Thread*, object, object, object),
                object (*makeError)(Thread*, object))
{
  object originalClass = class_;
  PROTECT(t, class_);

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
  }

  if (o == 0) {
    object message = makeString
      (t, "%s %s not found in %s",
       &byteArrayBody(t, name, 0),
       &byteArrayBody(t, spec, 0),
       &byteArrayBody(t, className(t, originalClass), 0));
    t->exception = makeError(t, message);
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

  object f = makeFinalizer(t, 0, function, 0);
  finalizerTarget(t, f) = target;
  finalizerNext(t, f) = t->m->finalizers;
  t->m->finalizers = f;
}

System::Monitor*
objectMonitor(Thread* t, object o, bool createNew)
{
  assert(t, t->state == Thread::ActiveState);

  object p = hashMapFind(t, t->m->monitorMap, o, objectHash, objectEqual);

  if (p) {
    if (DebugMonitors) {
      fprintf(stderr, "found monitor %p for object %x\n",
              static_cast<System::Monitor*>(pointerValue(t, p)),
              objectHash(t, o));
    }

    return static_cast<System::Monitor*>(pointerValue(t, p));
  } else if (createNew) {
    PROTECT(t, o);

    ENTER(t, Thread::ExclusiveState);

    p = hashMapFind(t, t->m->monitorMap, o, objectHash, objectEqual);
    if (p) {
      if (DebugMonitors) {
        fprintf(stderr, "found monitor %p for object %x\n",
                static_cast<System::Monitor*>(pointerValue(t, p)),
                objectHash(t, o));
      }

      return static_cast<System::Monitor*>(pointerValue(t, p));
    }

    System::Monitor* m;
    System::Status s = t->m->system->make(&m);
    expect(t, t->m->system->success(s));

    if (DebugMonitors) {
      fprintf(stderr, "made monitor %p for object %x\n",
              m,
              objectHash(t, o));
    }

    p = makePointer(t, m);
    hashMapInsert(t, t->m->monitorMap, o, p, objectHash);

    addFinalizer(t, o, removeMonitor);

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

  object n = hashMapFindNode(t, t->m->stringMap, s, stringHash, stringEqual);
  if (n) {
    return jreferenceTarget(t, tripleFirst(t, n));
  } else {
    hashMapInsert(t, t->m->stringMap, s, 0, stringHash);
    addFinalizer(t, s, removeString);
    return s;
  }
}

void
collect(Thread* t, Heap::CollectionType type)
{
  ENTER(t, Thread::ExclusiveState);

#ifdef VM_STRESS
  bool stress = t->stress;
  if (not stress) t->stress = true;
#endif

  Machine* m = t->m;

  m->unsafe = true;
  m->heap->collect(type, footprint(m->rootThread));
  m->unsafe = false;

  postCollect(m->rootThread);

  for (object f = m->finalizeQueue; f; f = finalizerNext(t, f)) {
    void (*function)(Thread*, object);
    memcpy(&function, &finalizerFinalize(t, f), BytesPerWord);
    function(t, finalizerTarget(t, f));
  }
  m->finalizeQueue = 0;

  killZombies(t, m->rootThread);

  for (unsigned i = 0; i < m->heapPoolIndex; ++i) {
    m->heap->free(m->heapPool[i], ThreadHeapSizeInBytes);
  }
  m->heapPoolIndex = 0;

  m->fixedFootprint = 0;

#ifdef VM_STRESS
  if (not stress) t->stress = false;
#endif
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
         cast<uintptr_t>(o, fixedSize - BytesPerWord) : 0);

    uint32_t mask[intArrayLength(t, objectMask)];
    memcpy(mask, &intArrayBody(t, objectMask, 0),
           intArrayLength(t, objectMask) * 4);

    more = ::walk(t, w, mask, fixedSize, arrayElementSize, arrayLength, start);
  } else if (classFlags(t, class_) & SingletonFlag) {
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

  if (more and classFlags(t, class_) & ContinuationFlag) {
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
  v->visit(&(m->loader));
  v->visit(&(m->classMap));
  v->visit(&(m->bootstrapClassMap));
  v->visit(&(m->monitorMap));
  v->visit(&(m->stringMap));
  v->visit(&(m->byteArrayMap));
  v->visit(&(m->types));
  v->visit(&(m->jniMethodTable));

  for (Thread* t = m->rootThread; t; t = t->peer) {
    ::visitRoots(t, v);
  }

  for (Reference* r = m->jniReferences; r; r = r->next) {
    v->visit(&(r->target));
  }
}

void
printTrace(Thread* t, object exception)
{
  if (exception == 0) {
    exception = makeNullPointerException(t, 0, makeTrace(t), 0);
  }

  for (object e = exception; e; e = throwableCause(t, e)) {
    if (e != exception) {
      fprintf(stderr, "caused by: ");
    }

    fprintf(stderr, "%s", &byteArrayBody
            (t, className(t, objectClass(t, e)), 0));
  
    if (throwableMessage(t, e)) {
      object m = throwableMessage(t, e);
      char message[stringLength(t, m) + 1];
      stringChars(t, m, message);
      fprintf(stderr, ": %s\n", message);
    } else {
      fprintf(stderr, "\n");
    }

    object trace = throwableTrace(t, e);
    for (unsigned i = 0; i < arrayLength(t, trace); ++i) {
      object e = arrayBody(t, trace, i);
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, traceElementMethod(t, e))), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, traceElementMethod(t, e)), 0);
      int line = t->m->processor->lineNumber
        (t, traceElementMethod(t, e), traceElementIp(t, e));

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
}

object
makeTrace(Thread* t, Processor::StackWalker* walker)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t), trace(0), index(0), protector(t, &trace) { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (trace == 0) {
        trace = makeArray(t, walker->count());
      }

      object e = makeTraceElement(t, walker->method(), walker->ip());
      assert(t, index < arrayLength(t, trace));
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

  return v.trace ? v.trace : makeArray(t, 0);
}

object
makeTrace(Thread* t, Thread* target)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t), trace(0) { }

    virtual bool visit(Processor::StackWalker* walker) {
      trace = makeTrace(t, walker);
      return false;
    }

    Thread* t;
    object trace;
  } v(t);

  t->m->processor->walkStack(target, &v);

  return v.trace ? v.trace : makeArray(t, 0);
}

void
runJavaThread(Thread* t)
{
  object method = resolveMethod
    (t, "java/lang/Thread", "run", "(Ljava/lang/Thread;)V");

  if (t->exception == 0) {
    t->m->processor->invoke(t, method, 0, t->javaThread);
  }
}

void
noop()
{ }

#include "type-constructors.cpp"

} // namespace vm

// for debugging
void
vmPrintTrace(Thread* t)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t) { }

    virtual bool visit(Processor::StackWalker* walker) {
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, walker->method())), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, walker->method()), 0);
      int line = t->m->processor->lineNumber
        (t, walker->method(), walker->ip());

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

      return true;
    }

    Thread* t;
  } v(t);

  t->m->processor->walkStack(t, &v);
}
