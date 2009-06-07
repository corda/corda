/* Copyright (c) 2008-2009, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"
#include "constants.h"
#include "processor.h"

using namespace vm;

namespace {

inline void
replace(char a, char b, char* c)
{
  for (; *c; ++c) if (*c == a) *c = b;
}

int64_t
search(Thread* t, object name, object (*op)(Thread*, object),
       bool replaceDots)
{
  if (LIKELY(name)) {
    object n = makeByteArray(t, stringLength(t, name) + 1);
    char* s = reinterpret_cast<char*>(&byteArrayBody(t, n, 0));
    stringChars(t, name, s);
    
    if (replaceDots) {
      replace('.', '/', s);
    }

    object r = op(t, n);
    if (t->exception) {
      return 0;
    }

    return reinterpret_cast<int64_t>(r);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

void
enumerateThreads(Thread* t, Thread* x, object array, unsigned* index,
                 unsigned limit)
{
  if (*index < limit) {
    set(t, array, ArrayBody + (*index * BytesPerWord), x->javaThread);
    ++ (*index);

    if (x->peer) enumerateThreads(t, x->peer, array, index, limit);
    
    if (x->child) enumerateThreads(t, x->child, array, index, limit);
  }
}

bool
compatibleArrayTypes(Thread* t, object a, object b)
{
  return classArrayElementSize(t, a)
    and classArrayElementSize(t, b)
    and (a == b
         or (not ((classVmFlags(t, a) & PrimitiveFlag)
                  or (classVmFlags(t, b) & PrimitiveFlag))));
}

} // namespace

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_toString
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  unsigned hash = objectHash(t, this_);
  object s = makeString
    (t, "%s@0x%x",
     &byteArrayBody(t, className(t, objectClass(t, this_)), 0),
     hash);

  return reinterpret_cast<int64_t>(s);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_getClass
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  return reinterpret_cast<int64_t>(objectClass(t, this_));
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Object_wait
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  int64_t milliseconds; memcpy(&milliseconds, arguments + 1, 8);

  vm::wait(t, this_, milliseconds);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Object_notify
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  notify(t, this_);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Object_notifyAll
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  notifyAll(t, this_);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_hashCode
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  return objectHash(t, this_);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Object_clone
(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, o);

  object class_ = objectClass(t, o);
  unsigned size = baseSize(t, o, class_) * BytesPerWord;
  object clone;

  if (classArrayElementSize(t, class_)) {
    clone = static_cast<object>(allocate(t, size, classObjectMask(t, class_)));
    memcpy(clone, o, size);
    // clear any object header flags:
    setObjectClass(t, o, objectClass(t, o));
  } else {
    clone = make(t, class_);
    memcpy(reinterpret_cast<void**>(clone) + 1,
           reinterpret_cast<void**>(o) + 1,
           size - BytesPerWord);
  }

  return reinterpret_cast<int64_t>(clone);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_ClassLoader_defineClass
(Thread* t, object, uintptr_t* arguments)
{
  object b = reinterpret_cast<object>(arguments[0]);
  int offset = arguments[1];
  int length = arguments[2];

  uint8_t* buffer = static_cast<uint8_t*>
    (t->m->heap->allocate(length));
  memcpy(buffer, &byteArrayBody(t, b, offset), length);
  object c = parseClass(t, buffer, length);
  t->m->heap->free(buffer, length);
  return reinterpret_cast<int64_t>(c);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findLoadedClass
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  return search(t, name, findLoadedClass, true);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findClass
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  return search(t, name, resolveClass, true);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_resourceExists
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);

  if (LIKELY(name)) {
    char n[stringLength(t, name) + 1];
    stringChars(t, name, n);
    return t->m->finder->exists(n);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_io_ObjectInputStream_makeInstance
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);

  return reinterpret_cast<int64_t>(make(t, c));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_primitiveClass
(Thread* t, object, uintptr_t* arguments)
{
  char name = arguments[0];

  switch (name) {
  case 'B':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JbyteType));
  case 'C':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JcharType));
  case 'D':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JdoubleType));
  case 'F':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JfloatType));
  case 'I':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JintType));
  case 'J':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JlongType));
  case 'S':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JshortType));
  case 'V':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JvoidType));
  case 'Z':
    return reinterpret_cast<int64_t>
      (arrayBody(t, t->m->types, Machine::JbooleanType));
  default:
    t->exception = makeIllegalArgumentException(t);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Class_initialize
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  initClass(t, this_);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_isAssignableFrom
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object that = reinterpret_cast<object>(arguments[1]);

  if (LIKELY(that)) {
    return vm::isAssignableFrom(t, this_, that);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getPrimitive
(Thread* t, object, uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[0]);
  int code = arguments[1];
  int offset = arguments[2];

  switch (code) {
  case ByteField: 
    return cast<int8_t>(instance, offset);
  case BooleanField: 
    return cast<uint8_t>(instance, offset);
  case CharField: 
    return cast<uint16_t>(instance, offset);
  case ShortField: 
    return cast<int16_t>(instance, offset);
  case IntField: 
    return cast<int32_t>(instance, offset);
  case LongField: 
    return cast<int64_t>(instance, offset);
  case FloatField: 
    return cast<uint32_t>(instance, offset);
  case DoubleField: 
    return cast<uint64_t>(instance, offset);
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Field_getObject
(Thread*, object, uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[0]);
  int offset = arguments[1];

  return reinterpret_cast<int64_t>(cast<object>(instance, offset));
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_reflect_Field_setPrimitive
(Thread* t, object, uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[0]);
  int code = arguments[1];
  int offset = arguments[2];
  int64_t value; memcpy(&value, arguments + 3, 8);

  switch (code) {
  case ByteField:
    cast<int8_t>(instance, offset) = static_cast<int8_t>(value);
    break;
  case BooleanField:
    cast<uint8_t>(instance, offset) = static_cast<uint8_t>(value);
    break;
  case CharField:
    cast<uint16_t>(instance, offset) = static_cast<uint16_t>(value);
    break;
  case ShortField:
    cast<int16_t>(instance, offset) = static_cast<int16_t>(value);
    break;
  case IntField: 
    cast<int32_t>(instance, offset) = static_cast<int32_t>(value);
    break;
  case LongField: 
    cast<int64_t>(instance, offset) = static_cast<int64_t>(value);
    break;
  case FloatField: 
    cast<uint32_t>(instance, offset) = static_cast<uint32_t>(value);
    break;
  case DoubleField: 
    cast<uint64_t>(instance, offset) = static_cast<uint64_t>(value);
    break;
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_reflect_Field_setObject
(Thread* t, object, uintptr_t* arguments)
{
  object instance = reinterpret_cast<object>(arguments[0]);
  int offset = arguments[1];
  object value = reinterpret_cast<object>(arguments[2]);

  set(t, instance, offset, value);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Constructor_make
(Thread* t, object, uintptr_t* arguments)
{
  object c = reinterpret_cast<object>(arguments[0]);

  return reinterpret_cast<int64_t>(make(t, c));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_getCaller
(Thread* t, object, uintptr_t*)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t): t(t), method(0), count(0) { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (count == 2) {
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
  } v(t);

  t->m->processor->walkStack(t, &v);

  return reinterpret_cast<int64_t>(v.method);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Method_invoke
(Thread* t, object, uintptr_t* arguments)
{
  object method = reinterpret_cast<object>(arguments[0]);
  object instance = reinterpret_cast<object>(arguments[1]);
  object args = reinterpret_cast<object>(arguments[2]);

  object v = t->m->processor->invokeArray(t, method, instance, args);
  if (t->exception) {
    t->exception = makeInvocationTargetException(t, t->exception);
  }
  return reinterpret_cast<int64_t>(v);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Array_getLength
(Thread* t, object, uintptr_t* arguments)
{
  object array = reinterpret_cast<object>(arguments[0]);

  if (LIKELY(array)) {
    unsigned elementSize = classArrayElementSize(t, objectClass(t, array));

    if (LIKELY(elementSize)) {
      return cast<uintptr_t>(array, BytesPerWord);
    } else {
      t->exception = makeIllegalArgumentException(t);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }
  return 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_reflect_Array_makeObjectArray
(Thread* t, object, uintptr_t* arguments)
{
  object elementType = reinterpret_cast<object>(arguments[0]);
  int length = arguments[1];

  return reinterpret_cast<int64_t>(makeObjectArray(t, elementType, length));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Float_floatToRawIntBits
(Thread*, object, uintptr_t* arguments)
{
  return static_cast<int32_t>(*arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Float_intBitsToFloat
(Thread*, object, uintptr_t* arguments)
{
  return static_cast<int32_t>(*arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Double_doubleToRawLongBits
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  return v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Double_longBitsToDouble
(Thread*, object, uintptr_t* arguments)
{
  int64_t v; memcpy(&v, arguments, 8);
  return v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_String_intern
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  return reinterpret_cast<int64_t>(intern(t, this_));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_getVMProperty
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  object found = reinterpret_cast<object>(arguments[1]);
  PROTECT(t, found);

  unsigned length = stringLength(t, name);
  char n[length + 1];
  stringChars(t, name, n);

  int64_t r = 0;
  if (strcmp(n, "java.lang.classpath") == 0) {
    r = reinterpret_cast<int64_t>(makeString(t, "%s", t->m->finder->path()));
  } else if (strcmp(n, "avian.version") == 0) {
    r = reinterpret_cast<int64_t>(makeString(t, AVIAN_VERSION));
  } else if (strcmp(n, "file.encoding") == 0) {
    r = reinterpret_cast<int64_t>(makeString(t, "ASCII"));
  } else {
    const char* v = findProperty(t, n);
    if (v) {
      r = reinterpret_cast<int64_t>(makeString(t, v));
    }
  }
  
  if (r) {
    booleanArrayBody(t, found, 0) = true;
  }

  return r;
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_System_arraycopy
(Thread* t, object, uintptr_t* arguments)
{
  object src = reinterpret_cast<object>(arguments[0]);
  int32_t srcOffset = arguments[1];
  object dst = reinterpret_cast<object>(arguments[2]);
  int32_t dstOffset = arguments[3];
  int32_t length = arguments[4];

  if (LIKELY(src and dst)) {
    if (LIKELY(compatibleArrayTypes
               (t, objectClass(t, src), objectClass(t, dst))))
    {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, src));

      if (LIKELY(elementSize)) {
        intptr_t sl = cast<uintptr_t>(src, BytesPerWord);
        intptr_t dl = cast<uintptr_t>(dst, BytesPerWord);
        if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl and
                   dstOffset >= 0 and dstOffset + length <= dl))
        {
          uint8_t* sbody = &cast<uint8_t>(src, ArrayBody);
          uint8_t* dbody = &cast<uint8_t>(dst, ArrayBody);
          if (src == dst) {
            memmove(dbody + (dstOffset * elementSize),
                    sbody + (srcOffset * elementSize),
                    length * elementSize);
          } else {
            memcpy(dbody + (dstOffset * elementSize),
                   sbody + (srcOffset * elementSize),
                   length * elementSize);
          }

          if (classObjectMask(t, objectClass(t, dst))) {
            mark(t, dst, ArrayBody + (dstOffset * BytesPerWord), length);
          }

          return;
        }
      }
    }
  } else {
    t->exception = makeNullPointerException(t);
    return;
  }

  t->exception = makeArrayStoreException(t);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_identityHashCode
(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[0]);

  if (LIKELY(o)) {
    return objectHash(t, o);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_load
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  bool mapName = arguments[1];

  unsigned length = stringLength(t, name);
  char n[length + 1];
  stringChars(t, name, n);

  ACQUIRE(t, t->m->classLock);

  const char* builtins = findProperty(t, "avian.builtins");
  if (mapName and builtins) {
    const char* s = builtins;
    while (*s) {
      if (strncmp(s, n, length) == 0
          and (s[length] == ',' or s[length] == 0))
      {
        // library is built in to this executable
        return;
      } else {
        while (*s and *s != ',') ++ s;
        if (*s) ++ s;
      }
    }
  }

  System::Library* last = t->m->libraries;
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    if (lib->name()
        and strcmp(lib->name(), n) == 0
        and lib->mapName() == mapName)
    {
      // already loaded
      return;
    }
    last = lib;
  }

  System::Library* lib;
  if (LIKELY(t->m->system->success(t->m->system->load(&lib, n, mapName)))) {
    last->setNext(lib);
  } else {
    object message = makeString(t, "library not found: %s", n);
    t->exception = makeUnsatisfiedLinkError(t, message);
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_gc
(Thread* t, object, uintptr_t*)
{
  collect(t, Heap::MajorCollection);
}

#ifdef AVIAN_HEAPDUMP

extern "C" JNIEXPORT void JNICALL
Avian_avian_Machine_dumpHeap
(Thread* t, object, uintptr_t* arguments)
{
  object outputFile = reinterpret_cast<object>(*arguments);

  unsigned length = stringLength(t, outputFile);
  char n[length + 1];
  stringChars(t, outputFile, n);
  FILE* out = fopen(n, "wb");
  if (out) {
    { ENTER(t, Thread::ExclusiveState);
      dumpHeap(t, out);
    }
    fclose(out);
  } else {
    object message = makeString(t, "file not found: %s", n);
    t->exception = makeRuntimeException(t, message);
  }
}

#endif//AVIAN_HEAPDUMP

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_exit
(Thread* t, object, uintptr_t* arguments)
{
  t->m->system->exit(*arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Runtime_freeMemory
(Thread*, object, uintptr_t*)
{
  // todo
  return 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Runtime_totalMemory
(Thread*, object, uintptr_t*)
{
  // todo
  return 0;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Throwable_trace
(Thread* t, object, uintptr_t* arguments)
{
  int32_t skipCount = arguments[0];

  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, int skipCount):
      t(t), trace(0), skipCount(skipCount)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipCount == 0) {
        object method = walker->method();
        if (isAssignableFrom
            (t, arrayBody(t, t->m->types, Machine::ThrowableType),
             methodClass(t, method))
            and strcmp(reinterpret_cast<const int8_t*>("<init>"),
                       &byteArrayBody(t, methodName(t, method), 0))
            == 0)
        {
          return true;
        } else {
          trace = makeTrace(t, walker);
          return false;
        }
      } else {
        -- skipCount;
        return true;
      }
    }

    Thread* t;
    object trace;
    unsigned skipCount;
  } v(t, skipCount);

  t->m->processor->walkStack(t, &v);

  if (v.trace == 0) v.trace = makeArray(t, 0);

  return reinterpret_cast<int64_t>(v.trace);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Throwable_resolveTrace
(Thread* t, object, uintptr_t* arguments)
{
  object trace = reinterpret_cast<object>(*arguments);
  PROTECT(t, trace);

  unsigned length = arrayLength(t, trace);
  object array = makeObjectArray
    (t, arrayBody(t, t->m->types, Machine::StackTraceElementType), length);
  PROTECT(t, array);

  object e = 0;
  PROTECT(t, e);

  object class_ = 0;
  PROTECT(t, class_);

  for (unsigned i = 0; i < length; ++i) {
    e = arrayBody(t, trace, i);

    class_ = className(t, methodClass(t, traceElementMethod(t, e)));
    class_ = makeString(t, class_, 0, byteArrayLength(t, class_) - 1, 0);

    object method = methodName(t, traceElementMethod(t, e));
    method = makeString(t, method, 0, byteArrayLength(t, method) - 1, 0);

    unsigned line = t->m->processor->lineNumber
      (t, traceElementMethod(t, e), traceElementIp(t, e));

    object ste = makeStackTraceElement(t, class_, method, 0, line);
    set(t, array, ArrayBody + (i * BytesPerWord), ste);
  }

  return reinterpret_cast<int64_t>(array);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Thread_currentThread
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>(t->javaThread);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Thread_doStart
(Thread* t, object, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(*arguments);

  Thread* p = t->m->processor->makeThread(t->m, this_, t);

  if (t->m->system->success(t->m->system->start(&(p->runnable)))) {
    return reinterpret_cast<int64_t>(p);
  } else {
    p->exit();
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Thread_interrupt
(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  interrupt(t, reinterpret_cast<Thread*>(peer));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Thread_getStackTrace
(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  if (reinterpret_cast<Thread*>(peer) == t) {
    return reinterpret_cast<int64_t>(makeTrace(t));
  } else {
    return reinterpret_cast<int64_t>
      (t->m->processor->getStackTrace(t, reinterpret_cast<Thread*>(peer)));
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Thread_activeCount
(Thread* t, object, uintptr_t*)
{
  return t->m->liveCount;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Thread_enumerate
(Thread* t, object, uintptr_t* arguments)
{
  object array = reinterpret_cast<object>(*arguments);

  ACQUIRE_RAW(t, t->m->stateLock);

  unsigned count = min(t->m->liveCount, objectArrayLength(t, array));
  unsigned index = 0;
  enumerateThreads(t, t->m->rootThread, array, &index, count);
  return count;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_resource_Handler_00024ResourceInputStream_getContentLength
(Thread* t, object, uintptr_t* arguments)
{
  object path = reinterpret_cast<object>(*arguments);

  if (LIKELY(path)) {
    char p[stringLength(t, path) + 1];
    stringChars(t, path, p);

    System::Region* r = t->m->finder->find(p);
    if (r) {
      jint rSize = r->length();
      r->dispose();
      return rSize;
    }
  }
  return -1;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_resource_Handler_00024ResourceInputStream_open
(Thread* t, object, uintptr_t* arguments)
{
  object path = reinterpret_cast<object>(*arguments);

  if (LIKELY(path)) {
    char p[stringLength(t, path) + 1];
    stringChars(t, path, p);

    return reinterpret_cast<int64_t>(t->m->finder->find(p));
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_resource_Handler_00024ResourceInputStream_read__JI
(Thread*, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];

  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (position >= static_cast<jint>(region->length())) {
    return -1;
  } else {
    return region->start()[position];
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_resource_Handler_00024ResourceInputStream_read__JI_3BII
(Thread* t, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int32_t position = arguments[2];
  object buffer = reinterpret_cast<object>(arguments[3]);
  int32_t offset = arguments[4];
  int32_t length = arguments[5];

  if (length == 0) return 0;
  
  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (length > static_cast<jint>(region->length()) - position) {
    length = static_cast<jint>(region->length()) - position;
  }
  if (length <= 0) {
    return -1;
  } else {
    memcpy(&byteArrayBody(t, buffer, offset), region->start() + position,
           length);
    return length;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_resource_Handler_00024ResourceInputStream_close
(Thread*, object, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  reinterpret_cast<System::Region*>(peer)->dispose();
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_callWithCurrentContinuation
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->callWithCurrentContinuation
    (t, reinterpret_cast<object>(*arguments));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_dynamicWind2
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->dynamicWind
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]),
     reinterpret_cast<object>(arguments[2]));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_00024Continuation_handleResult
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->feedResultToContinuation
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]));

  abort(t);
}

extern "C" JNIEXPORT void JNICALL
Avian_avian_Continuations_00024Continuation_handleException
(Thread* t, object, uintptr_t* arguments)
{
  t->m->processor->feedExceptionToContinuation
    (t, reinterpret_cast<object>(arguments[0]),
     reinterpret_cast<object>(arguments[1]));

  abort(t);
}
