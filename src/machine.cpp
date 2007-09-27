#include "jnienv.h"
#include "machine.h"
#include "stream.h"
#include "constants.h"
#include "processor.h"

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
    o->state = Thread::JoinedState;
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
      t->m->rootThread = o->child;
      if (o->peer) {
        o->peer->peer = o->child->peer;
        o->child->peer = o->peer;
      }      
    } else if (o->peer) {
      t->m->rootThread = o->peer;
    } else {
      abort(t);
    }

    assert(t, not find(t->m->rootThread, o));
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
  unsigned n = t->heapOffset + t->heapIndex;

  if (t->large) {
    n += extendedSize
      (t, t->large, baseSize(t, t->large, objectClass(t, t->large)));
  }

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
      v->visit(p->p);
    }
  }

  for (Thread* c = t->child; c; c = c->peer) {
    visitRoots(c, v);
  }
}

void
finalizerTargetUnreachable(Thread* t, object* p, Heap::Visitor* v)
{
  v->visit(&finalizerTarget(t, *p));

  object finalizer = *p;
  *p = finalizerNext(t, finalizer);
  finalizerNext(t, finalizer) = t->m->finalizeQueue;
  t->m->finalizeQueue = finalizer;
}

void
referenceTargetUnreachable(Thread* t, object* p, Heap::Visitor* v)
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

    set(t, jreferenceJnext(t, *p), *p);
    if (referenceQueueFront(t, q)) {
      set(t, jreferenceJnext(t, referenceQueueRear(t, q)), *p);
    } else {
      set(t, referenceQueueFront(t, q), *p);
    }
    set(t, referenceQueueRear(t, q), *p);

    jreferenceQueue(t, *p) = 0;
  }

  *p = jreferenceNext(t, *p);
}

void
referenceUnreachable(Thread* t, object* p, Heap::Visitor* v)
{
  if (DebugReferences) {
    fprintf(stderr, "reference %p unreachable (target %p)\n",
            *p, jreferenceTarget(t, *p));
  }

  if (jreferenceQueue(t, *p)
      and t->m->heap->status(jreferenceQueue(t, *p)) != Heap::Unreachable)
  {
    // queue is reachable - add the reference
    referenceTargetUnreachable(t, p, v);    
  } else {
    *p = jreferenceNext(t, *p);
  }
}

void
referenceTargetReachable(Thread* t, object* p, Heap::Visitor* v)
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

  for (object* p = &(m->finalizeQueue); *p; p = &(finalizerNext(t, *p))) {
    v->visit(p);
    v->visit(&finalizerTarget(t, *p));
  }

  for (object* p = &(m->finalizeQueue); *p; p = &(finalizerNext(t, *p))) {
    v->visit(p);
    v->visit(&finalizerTarget(t, *p));
  }

  object firstNewTenuredFinalizer = 0;
  object lastNewTenuredFinalizer = 0;

  for (object* p = &(m->finalizers); *p;) {
    v->visit(p);

    if (m->heap->status(finalizerTarget(t, *p)) == Heap::Unreachable) {
      // target is unreachable - queue it up for finalization
      finalizerTargetUnreachable(t, p, v);
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
      referenceUnreachable(t, p, v);
    } else if (m->heap->status(jreferenceTarget(t, *p))
               == Heap::Unreachable)
    {
      // target is unreachable
      referenceTargetUnreachable(t, p, v);
    } else {
      // both reference and target are reachable
      referenceTargetReachable(t, p, v);

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
        finalizerTargetUnreachable(t, p, v);
      } else {
        // target is reachable
        v->visit(&finalizerTarget(t, *p));
        p = &finalizerNext(t, *p);
      }
    }

    for (object* p = &(m->tenuredWeakReferences); *p;) {
      if (m->heap->status(*p) == Heap::Unreachable) {
        // reference is unreachable
        referenceUnreachable(t, p, v);
      } else if (m->heap->status(jreferenceTarget(t, *p))
                 == Heap::Unreachable)
      {
        // target is unreachable
        referenceTargetUnreachable(t, p, v);
      } else {
        // both reference and target are reachable
        referenceTargetReachable(t, p, v);
        p = &jreferenceNext(t, *p);
      }
    }
  }

  if (lastNewTenuredFinalizer) {
    finalizerNext(t, lastNewTenuredFinalizer) = m->tenuredFinalizers;
    m->tenuredFinalizers = firstNewTenuredFinalizer;
  }

  if (lastNewTenuredWeakReference) {
    jreferenceNext(t, lastNewTenuredWeakReference) = m->tenuredWeakReferences;
    m->tenuredWeakReferences = firstNewTenuredWeakReference;
  }
}

void
postCollect(Thread* t)
{
#ifdef VM_STRESS
  t->m->system->free(t->defaultHeap);
  t->defaultHeap = static_cast<uintptr_t*>
    (t->m->system->allocate(Thread::HeapSizeInBytes));
#endif

  t->heap = t->defaultHeap;
  t->heapOffset = 0;
  t->heapIndex = 0;

  if (t->large) {
    t->m->system->free(t->large);
    t->large = 0;
  }

  for (Thread* c = t->child; c; c = c->peer) {
    postCollect(c);
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
      index += mangle(byteArrayBody(t, methodSpec, i),
                      &byteArrayBody(t, name, index));
    }
  }

  byteArrayBody(t, name, index++) = 0;

  assert(t, index == size + 1);

  return name;
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
        == arrayBody(t, t->m->types, Machine::IntArrayType))
    {
      switch (intArrayBody(t, o, 0)) {
      case CONSTANT_Class: {
        set(t, arrayBody(t, pool, i),
            arrayBody(t, pool, intArrayBody(t, o, 1) - 1));
      } break;

      case CONSTANT_String: {
        object bytes = arrayBody(t, pool, intArrayBody(t, o, 1) - 1);
        object value = makeString
          (t, bytes, 0, byteArrayLength(t, bytes) - 1, 0);
        value = intern(t, value);
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
        == arrayBody(t, t->m->types, Machine::IntArrayType))
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
    object name = arrayBody(t, pool, s.read2() - 1);
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
        if (classVirtualTable(t, interface)) {
          // we'll fill in this table in parseMethodTable():
          object vtable = makeArray
            (t, arrayLength(t, classVirtualTable(t, interface)), true);
          
          set(t, arrayBody(t, interfaceTable, i), vtable);
        }

        ++i;
      }
    }
  }

  set(t, classInterfaceTable(t, class_), interfaceTable);
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
         0, // vm flags
         fieldCode(t, byteArrayBody(t, arrayBody(t, pool, spec - 1), 0)),
         flags,
         0, // offset
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
  
  if (classSuper(t, class_)
      and memberOffset == classFixedSize(t, classSuper(t, class_)))
  {
    set(t, classObjectMask(t, class_),
        classObjectMask(t, classSuper(t, class_)));
  } else {
    object mask = makeIntArray
      (t, ceiling(classFixedSize(t, class_), 32 * BytesPerWord), true);
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
      set(t, classObjectMask(t, class_), mask);
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

  object code = makeCode(t, pool, 0, 0, maxStack, maxLocals, length, false);
  s.read(&codeBody(t, code, 0), length);
  PROTECT(t, code);

  unsigned ehtLength = s.read2();
  if (ehtLength) {
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

void
scanMethodSpec(Thread* t, const char* s, unsigned* parameterCount,
               unsigned* returnCode)
{
  unsigned count = 0;
  ++ s; // skip '('
  while (*s and *s != ')') {
    switch (*s) {
    case 'L':
      while (*s and *s != ';') ++ s;
      ++ s;
      break;

    case '[':
      while (*s == '[') ++ s;
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        break;

      default:
        ++ s;
        break;
      }
      break;
      
    default:
      ++ s;
      break;
    }

    ++ count;
  }

  *parameterCount = count;
  *returnCode = fieldCode(t, s[1]);
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
                 methodName(t, method),
                 methodSpec(t, method),
                 methodClass(t, method),
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

      const char* specString = reinterpret_cast<const char*>
        (&byteArrayBody(t, arrayBody(t, pool, spec - 1), 0));

      unsigned parameterCount;
      unsigned returnCode;
      scanMethodSpec(t, specString, &parameterCount, &returnCode);

      unsigned parameterFootprint = t->m->processor->parameterFootprint
        (t, specString, flags & ACC_STATIC);

      object method = makeMethod(t,
                                 0, // vm flags
                                 returnCode,
                                 parameterCount,
                                 parameterFootprint,
                                 flags,
                                 0, // offset
                                 arrayBody(t, pool, name - 1),
                                 arrayBody(t, pool, spec - 1),
                                 class_,
                                 code,
                                 (code ? t->m->processor->methodStub(t) : 0));
      PROTECT(t, method);

      if (flags & ACC_STATIC) {
        methodOffset(t, method) = i;

        if (strcmp(reinterpret_cast<const int8_t*>("<clinit>"), 
                   &byteArrayBody(t, methodName(t, method), 0)) == 0)
        {
          methodVmFlags(t, method) |= ClassInitFlag;
          classVmFlags(t, class_) |= NeedInitFlag;
        }
      } else {
        ++ declaredVirtualCount;

        object p = hashMapFindNode
          (t, virtualMap, method, methodHash, methodEqual);

        if (p) {
          methodOffset(t, method) = methodOffset(t, tripleFirst(t, p));

          set(t, tripleSecond(t, p), method);
        } else {
          methodOffset(t, method) = virtualCount++;

          listAppend(t, newVirtuals, method);

          hashMapInsert(t, virtualMap, method, method, methodHash);
        }
      }

      if (flags & ACC_NATIVE) {
        object p = hashMapFindNode
          (t, nativeMap, methodName(t, method), byteArrayHash, byteArrayEqual);
        
        if (p) {
          set(t, tripleSecond(t, p), method);          
        } else {
          hashMapInsert(t, nativeMap, methodName(t, method), 0, byteArrayHash);
        }
      }

      set(t, arrayBody(t, methodTable, i), method);
    }

    for (unsigned i = 0; i < count; ++i) {
      object method = arrayBody(t, methodTable, i);

      if (methodFlags(t, method) & ACC_NATIVE) {
        PROTECT(t, method);

        object overloaded = hashMapFind
          (t, nativeMap, methodName(t, method), byteArrayHash, byteArrayEqual);

        object jniName = makeJNIName(t, method, overloaded);
        set(t, methodCode(t, method), jniName);
      }
    }

    set(t, classMethodTable(t, class_), methodTable);
  }

  if (declaredVirtualCount == 0
      and (classFlags(t, class_) & ACC_INTERFACE) == 0)
  {
    // inherit interface table and virtual table from superclass

    set(t, classInterfaceTable(t, class_),
        classInterfaceTable(t, classSuper(t, class_)));

    set(t, classVirtualTable(t, class_), superVirtualTable);    
  } else if (virtualCount) {
    // generate class vtable

    object vtable = makeArray(t, virtualCount, true);

    unsigned i = 0;
    if (classFlags(t, class_) & ACC_INTERFACE) {
      PROTECT(t, vtable);

      for (object it = hashMapIterator(t, virtualMap); it;
           it = hashMapIteratorNext(t, it))
      {
        object method = tripleFirst(t, hashMapIteratorNode(t, it));
        assert(t, arrayBody(t, vtable, methodOffset(t, method)) == 0);
        set(t, arrayBody(t, vtable, methodOffset(t, method)), method);
        ++ i;
      }
    } else {
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
    }

    assert(t, arrayLength(t, vtable) == i);

    set(t, classVirtualTable(t, class_), vtable);

    if ((classFlags(t, class_) & ACC_INTERFACE) == 0) {
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
              
              set(t, arrayBody(t, vtable, j), method);        
            }
          }
        }
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
  expect(t, classFixedSize(t, bootstrapClass) == classFixedSize(t, class_));
  expect(t,
         (classVmFlags(t, bootstrapClass) & ReferenceFlag)
         or (classObjectMask(t, bootstrapClass) == 0
             and classObjectMask(t, class_) == 0)
         or intArrayEqual(t, classObjectMask(t, bootstrapClass),
                          classObjectMask(t, class_)));

  PROTECT(t, bootstrapClass);
  PROTECT(t, class_);

  ENTER(t, Thread::ExclusiveState);

  classFlags(t, bootstrapClass) = classFlags(t, class_);
  classVmFlags(t, bootstrapClass) |= classVmFlags(t, class_);

  set(t, classSuper(t, bootstrapClass), classSuper(t, class_));
  set(t, classInterfaceTable(t, bootstrapClass),
       classInterfaceTable(t, class_));
  set(t, classVirtualTable(t, bootstrapClass), classVirtualTable(t, class_));
  set(t, classFieldTable(t, bootstrapClass), classFieldTable(t, class_));
  set(t, classMethodTable(t, bootstrapClass), classMethodTable(t, class_));
  set(t, classStaticTable(t, bootstrapClass), classStaticTable(t, class_));

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
makeArrayClass(Thread* t, unsigned dimensions, object spec,
               object elementClass)
{
  // todo: arrays should implement Cloneable and Serializable
  return makeClass
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
     classVirtualTable(t, arrayBody(t, t->m->types, Machine::JobjectType)),
     0,
     0,
     elementClass,
     t->m->loader);
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
    
    elementSpec = makeByteArray(t, s - elementSpecStart + 1, false);
    memcpy(&byteArrayBody(t, elementSpec, 0),
           &byteArrayBody(t, spec, elementSpecStart - start),
           s - elementSpecStart);
    byteArrayBody(t, elementSpec, s - elementSpecStart) = 0;
  } break;

  default:
    if (dimensions > 1) {
      char c = *s;
      elementSpec = makeByteArray(t, 3, false);
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
  object p = hashMapRemove(t, t->m->monitorMap, o, objectHash, objectEqual);

  assert(t, p);

  if (DebugMonitors) {
    fprintf(stderr, "dispose monitor %p for object %x\n",
            static_cast<System::Monitor*>(pointerValue(t, p)),
            objectHash(t, o));
  }

  static_cast<System::Monitor*>(pointerValue(t, p))->dispose();
}

void
removeString(Thread* t, object o)
{
  hashMapRemove(t, t->m->stringMap, o, stringHash, objectEqual);
}

void
invoke(Thread* t, const char* className, int argc, const char** argv)
{
  enter(t, Thread::ActiveState);

  object args = makeObjectArray
    (t, arrayBody(t, t->m->types, Machine::StringType), argc, true);

  PROTECT(t, args);

  for (int i = 0; i < argc; ++i) {
    object arg = makeString(t, "%s", argv[i]);
    set(t, objectArrayBody(t, args, i), arg);
  }

  t->m->processor->invoke
    (t, className, "main", "([Ljava/lang/String;)V", 0, args);
}

} // namespace

namespace vm {

Machine::Machine(System* system, Heap* heap, Finder* finder,
                 Processor* processor):
  vtable(&javaVMVTable),
  system(system),
  heap(heap),
  finder(finder),
  processor(processor),
  rootThread(0),
  exclusive(0),
  jniReferences(0),
  activeCount(0),
  liveCount(0),
  localThread(0),
  stateLock(0),
  heapLock(0),
  classLock(0),
  referenceLock(0),
  libraries(0),
  loader(0),
  bootstrapClassMap(0),
  monitorMap(0),
  stringMap(0),
  types(0),
  jniInterfaceTable(0),
  finalizers(0),
  tenuredFinalizers(0),
  finalizeQueue(0),
  weakReferences(0),
  tenuredWeakReferences(0),
  unsafe(false),
  heapPoolIndex(0)
{
  populateJNITables(&javaVMVTable, &jniEnvVTable);

  if (not system->success(system->make(&localThread)) or
      not system->success(system->make(&stateLock)) or
      not system->success(system->make(&heapLock)) or
      not system->success(system->make(&classLock)) or
      not system->success(system->make(&referenceLock)) or
      not system->success(system->load(&libraries, 0, false, 0)))
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
    libraries->dispose();
  }

  for (Reference* r = jniReferences; r;) {
    Reference* t = r;
    r = r->next;
    system->free(t);
  }
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
  large(0),
  heapIndex(0),
  heapOffset(0),
  protector(0),
  runnable(this)
#ifdef VM_STRESS
  , stress(false),
  defaultHeap(static_cast<uintptr_t*>(m->system->allocate(HeapSizeInBytes)))
#endif // VM_STRESS
  , heap(defaultHeap)
{
  if (parent == 0) {
    assert(this, m->rootThread == 0);
    assert(this, javaThread == 0);

    m->rootThread = this;
    m->unsafe = true;

    if (not m->system->success(m->system->attach(&runnable))) {
      abort(this);
    }

    Thread* t = this;

    t->m->loader = allocate(t, sizeof(void*) * 3);
    memset(t->m->loader, 0, sizeof(void*) * 2);

#include "type-initializations.cpp"

    object arrayClass = arrayBody(t, t->m->types, Machine::ArrayType);
    set(t, cast<object>(t->m->types, 0), arrayClass);

    object loaderClass = arrayBody
      (t, t->m->types, Machine::SystemClassLoaderType);
    set(t, cast<object>(t->m->loader, 0), loaderClass);

    object objectClass = arrayBody(t, m->types, Machine::JobjectType);

    object classClass = arrayBody(t, m->types, Machine::ClassType);
    set(t, cast<object>(classClass, 0), classClass);
    set(t, classSuper(t, classClass), objectClass);

    object intArrayClass = arrayBody(t, m->types, Machine::IntArrayType);
    set(t, cast<object>(intArrayClass, 0), classClass);
    set(t, classSuper(t, intArrayClass), objectClass);

    m->unsafe = false;

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

    m->bootstrapClassMap = makeHashMap(this, 0, 0);

#include "type-java-initializations.cpp"

    object loaderMap = makeHashMap(this, 0, 0);
    set(t, systemClassLoaderMap(t, m->loader), loaderMap);

    m->monitorMap = makeWeakHashMap(this, 0, 0);
    m->stringMap = makeWeakHashMap(this, 0, 0);

    m->jniInterfaceTable = makeVector(this, 0, 0, false);

    m->localThread->set(this);
  } else {
    parent->child = this;
  }

  if (javaThread) {
    threadPeer(this, javaThread) = reinterpret_cast<jlong>(this);
  } else {
    this->javaThread = makeThread
      (this, reinterpret_cast<int64_t>(this), 0, 0, 0, 0, m->loader);
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
  if (large) {
    m->system->free(large);
    large = 0;
  }

  if (systemThread) {
    systemThread->dispose();
    systemThread = 0;
  }

#ifdef VM_STRESS
  m->system->free(heap);
  heap = 0;
#endif // VM_STRESS

  m->system->free(this);
}

void
exit(Thread* t)
{
  enter(t, Thread::ExitState);

  joinAll(t, t->m->rootThread);

  for (object* p = &(t->m->finalizers); *p;) {
    object f = *p;
    *p = finalizerNext(t, *p);

    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
  }

  for (object* p = &(t->m->tenuredFinalizers); *p;) {
    object f = *p;
    *p = finalizerNext(t, *p);

    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
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
    assert(t, t->state == Thread::ActiveState);

    while (t->m->exclusive) {
      // another thread got here first.
      ENTER(t, Thread::IdleState);
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
allocate2(Thread* t, unsigned sizeInBytes)
{
  if (sizeInBytes > Thread::HeapSizeInBytes and t->large == 0) {
    return allocateLarge(t, sizeInBytes);
  }

  ACQUIRE_RAW(t, t->m->stateLock);

  while (t->m->exclusive and t->m->exclusive != t) {
    // another thread wants to enter the exclusive state, either for a
    // collection or some other reason.  We give it a chance here.
    ENTER(t, Thread::IdleState);
  }

  if (t->heapIndex + ceiling(sizeInBytes, BytesPerWord)
      >= Thread::HeapSizeInWords)
  {
    t->heap = 0;
    if (t->large == 0 and t->m->heapPoolIndex < Machine::HeapPoolSize) {
      t->heap = static_cast<uintptr_t*>
        (t->m->system->tryAllocate(Thread::HeapSizeInBytes));
      if (t->heap) {
        t->m->heapPool[t->m->heapPoolIndex++] = t->heap;
        t->heapOffset += t->heapIndex;
        t->heapIndex = 0;
      }
    }

    if (t->heap == 0) {
      ENTER(t, Thread::ExclusiveState);
      collect(t, Heap::MinorCollection);
    }
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
    for (int i = 0; i < stringLength(t, string); ++i) {
      chars[i] = charArrayBody(t, data, stringOffset(t, string) + i);
    }
  }
  chars[stringLength(t, string)] = 0;
}

bool
isAssignableFrom(Thread* t, object a, object b)
{
  if (a == b) return true;

  if (classFlags(t, a) & ACC_INTERFACE) {
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

object
hashMapFindNode(Thread* t, object map, object key,
                uint32_t (*hash)(Thread*, object),
                bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    for (object n = arrayBody(t, array, index); n; n = tripleThird(t, n)) {
      object k = tripleFirst(t, n);
      if (weak) {
        k = jreferenceTarget(t, k);
      }

      if (equal(t, key, k)) {
        return n;
      }
    }
  }
  return 0;
}

void
hashMapResize(Thread* t, object map, uint32_t (*hash)(Thread*, object),
              unsigned size)
{
  PROTECT(t, map);

  object newArray = 0;

  if (size) {
    object oldArray = hashMapArray(t, map);
    PROTECT(t, oldArray);

    unsigned newLength = nextPowerOfTwo(size);
    if (oldArray and arrayLength(t, oldArray) == newLength) {
      return;
    }

    newArray = makeArray(t, newLength, true);

    if (oldArray) {
      bool weak = objectClass(t, map)
        == arrayBody(t, t->m->types, Machine::WeakHashMapType);

      for (unsigned i = 0; i < arrayLength(t, oldArray); ++i) {
        object next;
        for (object p = arrayBody(t, oldArray, i); p; p = next) {
          next = tripleThird(t, p);

          object k = tripleFirst(t, p);
          if (weak) {
            k = jreferenceTarget(t, k);
          }

          unsigned index = hash(t, k) & (newLength - 1);

          set(t, tripleThird(t, p), arrayBody(t, newArray, index));
          set(t, arrayBody(t, newArray, index), p);
        }
      }
    }
  }
  
  set(t, hashMapArray(t, map), newArray);
}

void
hashMapInsert(Thread* t, object map, object key, object value,
               uint32_t (*hash)(Thread*, object))
{
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

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

  if (weak) {
    PROTECT(t, key);
    PROTECT(t, value);

    object r = makeWeakReference(t, 0, 0, 0, 0);
    jreferenceTarget(t, r) = key;
    jreferenceNext(t, r) = t->m->weakReferences;
    key = t->m->weakReferences = r;
  }

  object n = makeTriple(t, key, value, arrayBody(t, array, index));

  set(t, arrayBody(t, array, index), n);
}

object
hashMapRemove(Thread* t, object map, object key,
              uint32_t (*hash)(Thread*, object),
              bool (*equal)(Thread*, object, object))
{
  bool weak = objectClass(t, map)
    == arrayBody(t, t->m->types, Machine::WeakHashMapType);

  object array = hashMapArray(t, map);
  object o = 0;
  if (array) {
    unsigned index = hash(t, key) & (arrayLength(t, array) - 1);
    for (object* n = &arrayBody(t, array, index); *n;) {
      object k = tripleFirst(t, *n);
      if (weak) {
        k = jreferenceTarget(t, k);
      }

      if (equal(t, key, k)) {
        o = tripleSecond(t, *n);
        set(t, *n, tripleThird(t, *n));
        -- hashMapSize(t, map);
        break;
      } else {
        n = &tripleThird(t, *n);
      }
    }

    if (hashMapSize(t, map) <= arrayLength(t, array) / 3) { 
      PROTECT(t, o);
      hashMapResize(t, map, hash, arrayLength(t, array) / 2);
    }
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
    return makeHashMapIterator(t, map, tripleThird(t, node), index);
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

object
vectorAppend(Thread* t, object vector, object value)
{
  if (vectorLength(t, vector) == vectorSize(t, vector)) {
    PROTECT(t, vector);
    PROTECT(t, value);

    object newVector = makeVector
      (t, vectorSize(t, vector), max(16, vectorSize(t, vector) * 2), false);

    if (vectorSize(t, vector)) {
      memcpy(&vectorBody(t, newVector, 0),
             &vectorBody(t, vector, 0),
             vectorSize(t, vector) * BytesPerWord);
    }

    memset(&vectorBody(t, newVector, vectorSize(t, vector) + 1),
           0,
           (vectorLength(t, newVector) - vectorSize(t, vector) - 1)
           * BytesPerWord);

    vector = newVector;
  }

  set(t, vectorBody(t, vector, vectorSize(t, vector)++), value);
  return vector;
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

  return hashMapFind(t, systemClassLoaderMap(t, t->m->loader),
                     spec, byteArrayHash, byteArrayEqual);
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
                            arrayBody(t, pool, name - 1),
                            0, // super
                            0, // interfaces
                            0, // vtable
                            0, // fields
                            0, // methods
                            0, // static table
                            t->m->loader);
  PROTECT(t, class_);
  
  unsigned super = s.read2();
  if (super) {
    object sc = resolveClass(t, arrayBody(t, pool, super - 1));
    if (UNLIKELY(t->exception)) return 0;

    set(t, classSuper(t, class_), sc);

    classVmFlags(t, class_)
      |= (classVmFlags(t, sc) & (ReferenceFlag | WeakReferenceFlag));
  }
  
  parseInterfaceTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseFieldTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  parseMethodTable(t, s, class_, pool);
  if (UNLIKELY(t->exception)) return 0;

  return class_;
}

object
resolveClass(Thread* t, object spec)
{
  PROTECT(t, spec);
  ACQUIRE(t, t->m->classLock);

  object class_ = hashMapFind(t, systemClassLoaderMap(t, t->m->loader),
                              spec, byteArrayHash, byteArrayEqual);
  if (class_ == 0) {
    if (byteArrayBody(t, spec, 0) == '[') {
      class_ = hashMapFind
        (t, t->m->bootstrapClassMap, spec, byteArrayHash, byteArrayEqual);

      if (class_ == 0) {
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
            fprintf(stderr, "done parsing %s\n", &byteArrayBody(t, spec, 0));
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

      hashMapInsert(t, systemClassLoaderMap(t, t->m->loader),
                    spec, class_, byteArrayHash);
    } else if (t->exception == 0) {
      object message = makeString(t, "%s", &byteArrayBody(t, spec, 0));
      t->exception = makeClassNotFoundException(t, message);
    }
  }

  return class_;
}

object
resolveMethod(Thread* t, const char* className, const char* methodName,
              const char* methodSpec)
{
  object class_ = resolveClass(t, makeByteArray(t, "%s", className));
  if (LIKELY(t->exception == 0)) {
    PROTECT(t, class_);

    object name = makeByteArray(t, methodName);
    PROTECT(t, name);

    object spec = makeByteArray(t, methodSpec);
    object reference = makeReference(t, class_, name, spec);
    
    return findMethodInClass(t, class_, referenceName(t, reference),
                             referenceSpec(t, reference));
  }

  return 0;
}

object
resolveObjectArrayClass(Thread* t, object elementSpec)
{
  PROTECT(t, elementSpec);

  object spec;
  if (byteArrayBody(t, elementSpec, 0) == '[') {
    spec = makeByteArray(t, byteArrayLength(t, elementSpec) + 1, false);
    byteArrayBody(t, spec, 0) = '[';
    memcpy(&byteArrayBody(t, spec, 1),
           &byteArrayBody(t, elementSpec, 0),
           byteArrayLength(t, elementSpec));
  } else {
    spec = makeByteArray(t, byteArrayLength(t, elementSpec) + 3, false);
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
makeObjectArray(Thread* t, object elementClass, unsigned count, bool clear)
{
  object arrayClass = resolveObjectArrayClass(t, className(t, elementClass));
  PROTECT(t, arrayClass);

  object array = makeArray(t, count, clear);
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
  if (classFlags(t, class_) & ACC_INTERFACE) {
    if (classVirtualTable(t, class_)) {
      o = findInTable
        (t, classVirtualTable(t, class_), name, spec, methodName, methodSpec);
    }
  } else {
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

void
addFinalizer(Thread* t, object target, void (*finalize)(Thread*, object))
{
  PROTECT(t, target);

  ACQUIRE(t, t->m->referenceLock);

  object f = makeFinalizer(t, 0, reinterpret_cast<void*>(finalize), 0);
  finalizerTarget(t, f) = target;
  finalizerNext(t, f) = t->m->finalizers;
  t->m->finalizers = f;
}

System::Monitor*
objectMonitor(Thread* t, object o)
{
  object p = hashMapFind(t, t->m->monitorMap, o, objectHash, objectEqual);

  if (p) {
    if (DebugMonitors) {
      fprintf(stderr, "found monitor %p for object %x\n",
              static_cast<System::Monitor*>(pointerValue(t, p)),
              objectHash(t, o));
    }

    return static_cast<System::Monitor*>(pointerValue(t, p));
  } else {
    PROTECT(t, o);

    ENTER(t, Thread::ExclusiveState);

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
  Machine* m = t->m;

  class Client: public Heap::Client {
   public:
    Client(Machine* m): m(m) { }

    virtual void visitRoots(Heap::Visitor* v) {
      v->visit(&(m->loader));
      v->visit(&(m->bootstrapClassMap));
      v->visit(&(m->monitorMap));
      v->visit(&(m->stringMap));
      v->visit(&(m->types));
      v->visit(&(m->jniInterfaceTable));

      for (Reference* r = m->jniReferences; r; r = r->next) {
        v->visit(&(r->target));
      }

      for (Thread* t = m->rootThread; t; t = t->peer) {
        ::visitRoots(t, v);
      }

      postVisit(m->rootThread, v);
    }

    virtual unsigned sizeInWords(void* p) {
      Thread* t = m->rootThread;

      object o = static_cast<object>(m->heap->follow(mask(p)));

      return extendedSize
        (t, o, baseSize(t, o, static_cast<object>
                        (m->heap->follow(objectClass(t, o)))));
    }

    virtual unsigned copiedSizeInWords(void* p) {
      Thread* t = m->rootThread;

      object o = static_cast<object>(m->heap->follow(mask(p)));

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
      Thread* t = m->rootThread;

      object o = static_cast<object>(m->heap->follow(mask(p)));
      object class_ = static_cast<object>(m->heap->follow(objectClass(t, o)));
      object objectMask = static_cast<object>
        (m->heap->follow(classObjectMask(t, class_)));

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

        unsigned fixedSizeInWords = ceiling(fixedSize, BytesPerWord);
        unsigned arrayElementSizeInWords
          = ceiling(arrayElementSize, BytesPerWord);

        for (unsigned i = 0; i < fixedSizeInWords; ++i) {
          if (mask[i / 32] & (static_cast<uintptr_t>(1) << (i % 32))) {
            if (not w->visit(i)) {
              return;
            }
          }
        }

        bool arrayObjectElements = false;
        for (unsigned j = 0; j < arrayElementSizeInWords; ++j) {
          unsigned k = fixedSizeInWords + j;
          if (mask[k / 32] & (static_cast<uintptr_t>(1) << (k % 32))) {
            arrayObjectElements = true;
            break;
          }
        }

        if (arrayObjectElements) {
          for (unsigned i = 0; i < arrayLength; ++i) {
            for (unsigned j = 0; j < arrayElementSizeInWords; ++j) {
              unsigned k = fixedSizeInWords + j;
              if (mask[k / 32] & (static_cast<uintptr_t>(1) << (k % 32))) {
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
  m->heap->collect(type, &it, footprint(m->rootThread));
  m->unsafe = false;

  postCollect(m->rootThread);

  for (object f = m->finalizeQueue; f; f = finalizerNext(t, f)) {
    reinterpret_cast<void (*)(Thread*, object)>(finalizerFinalize(t, f))
      (t, finalizerTarget(t, f));
  }
  m->finalizeQueue = 0;

  killZombies(t, m->rootThread);

  for (unsigned i = 0; i < m->heapPoolIndex; ++i) {
    m->system->free(m->heapPool[i]);
  }
  m->heapPoolIndex = 0;
}

void
printTrace(Thread* t, object exception)
{
  for (object e = exception; e; e = throwableCauseUnsafe(t, e)) {
    if (e != exception) {
      fprintf(stderr, "caused by: ");
    }

    fprintf(stderr, "%s", &byteArrayBody
            (t, className(t, objectClass(t, e)), 0));
  
    if (throwableMessageUnsafe(t, e)) {
      object m = throwableMessageUnsafe(t, e);
      char message[stringLength(t, m) + 1];
      stringChars(t, m, message);
      fprintf(stderr, ": %s\n", message);
    } else {
      fprintf(stderr, "\n");
    }

    object trace = throwableTraceUnsafe(t, e);
    for (unsigned i = 0; i < arrayLength(t, trace); ++i) {
      object e = arrayBody(t, trace, i);
      const int8_t* class_ = &byteArrayBody
        (t, className(t, methodClass(t, traceElementMethod(t, e))), 0);
      const int8_t* method = &byteArrayBody
        (t, methodName(t, traceElementMethod(t, e)), 0);
      int line = lineNumber(t, traceElementMethod(t, e), traceElementIp(t, e));

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

int
run(System* system, Heap* heap, Finder* finder, Processor* processor,
    const char* className, int argc, const char** argv)
{
  Machine m(system, heap, finder, processor);
  Thread* t = processor->makeThread(&m, 0, 0);

  enter(t, Thread::ActiveState);

  ::invoke(t, className, argc, argv);

  int exitCode = 0;
  if (t->exception) {
    exitCode = -1;
    printTrace(t, t->exception);
  }

  exit(t);

  return exitCode;
}

object
makeTrace(Thread* t, uintptr_t start)
{
  Processor* p = t->m->processor;

  unsigned count = 0;
  for (uintptr_t frame = start;
       p->frameValid(t, frame);
       frame = p->frameNext(t, frame))
  {
    ++ count;
  }

  object trace = makeArray(t, count, true);
  PROTECT(t, trace);
  
  unsigned index = 0;
  for (uintptr_t frame = start;
       p->frameValid(t, frame);
       frame = p->frameNext(t, frame))
  {
    object e = makeTraceElement
      (t, p->frameMethod(t, frame), p->frameIp(t, frame));
    set(t, arrayBody(t, trace, index++), e);
  }

  return trace;
}

void
noop()
{ }

#include "type-constructors.cpp"

} // namespace vm
