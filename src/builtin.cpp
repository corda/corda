/* Copyright (c) 2008, Avian Contributors

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

jclass
search(Thread* t, jstring name, object (*op)(Thread*, object),
       bool replaceDots)
{
  if (LIKELY(name)) {
    object n = makeByteArray(t, stringLength(t, *name) + 1, false);
    char* s = reinterpret_cast<char*>(&byteArrayBody(t, n, 0));
    stringChars(t, *name, s);
    
    if (replaceDots) {
      replace('.', '/', s);
    }

    object r = op(t, n);
    if (t->exception) {
      return 0;
    }

    return makeLocalReference(t, r);
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

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_Object_toString(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  unsigned hash = objectHash(t, *this_);
  object s = makeString
    (t, "%s@0x%x",
     &byteArrayBody(t, className(t, objectClass(t, *this_)), 0),
     hash);

  return makeLocalReference(t, s);
}

extern "C" JNIEXPORT jclass JNICALL
Java_java_lang_Object_getClass(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, objectClass(t, *this_));
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Object_wait(Thread* t, jobject this_, jlong milliseconds)
{
  ENTER(t, Thread::ActiveState);

  vm::wait(t, *this_, milliseconds);
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Object_notify(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  notify(t, *this_);
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Object_notifyAll(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  notifyAll(t, *this_);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Object_hashCode(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  return objectHash(t, *this_);
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_Object_clone(Thread* t, jclass, jobject o)
{
  ENTER(t, Thread::ActiveState);

  object class_ = objectClass(t, *o);
  unsigned size = baseSize(t, *o, class_) * BytesPerWord;
  object clone;

  if (classArrayElementSize(t, class_)) {
    clone = static_cast<object>(allocate(t, size, classObjectMask(t, class_)));
    memcpy(clone, *o, size);
    // clear any object header flags:
    setObjectClass(t, *o, objectClass(t, *o));
  } else {
    clone = make(t, class_);
    memcpy(reinterpret_cast<void**>(clone) + 1,
           reinterpret_cast<void**>(*o) + 1,
           size - BytesPerWord);
  }

  return makeLocalReference(t, clone);
}

extern "C" JNIEXPORT jclass JNICALL
Java_java_lang_ClassLoader_defineClass
(Thread* t, jclass, jbyteArray b, jint offset, jint length)
{
  ENTER(t, Thread::ActiveState);

  uint8_t* buffer = static_cast<uint8_t*>
    (t->m->heap->allocate(length));
  memcpy(buffer, &byteArrayBody(t, *b, offset), length);
  object c = parseClass(t, buffer, length);
  t->m->heap->free(buffer, length);
  return makeLocalReference(t, c);
}

extern "C" JNIEXPORT jclass JNICALL
Java_java_lang_SystemClassLoader_findLoadedClass
(Thread* t, jclass, jstring name)
{
  ENTER(t, Thread::ActiveState);

  return search(t, name, findLoadedClass, true);
}

extern "C" JNIEXPORT jclass JNICALL
Java_java_lang_SystemClassLoader_findClass(Thread* t, jclass, jstring name)
{
  ENTER(t, Thread::ActiveState);

  return search(t, name, resolveClass, true);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_java_lang_SystemClassLoader_resourceExists
(Thread* t, jclass, jstring name)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(name)) {
    char n[stringLength(t, *name) + 1];
    stringChars(t, *name, n);
    return t->m->finder->exists(n);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_io_ObjectInputStream_makeInstance(Thread* t, jclass, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, make(t, *c));
}

extern "C" JNIEXPORT jclass JNICALL
Java_java_lang_Class_primitiveClass(Thread* t, jclass, jchar name)
{
  ENTER(t, Thread::ActiveState);

  switch (name) {
  case 'B':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JbyteType));
  case 'C':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JcharType));
  case 'D':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JdoubleType));
  case 'F':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JfloatType));
  case 'I':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JintType));
  case 'J':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JlongType));
  case 'S':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JshortType));
  case 'V':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JvoidType));
  case 'Z':
    return makeLocalReference(t, arrayBody(t, t->m->types, Machine::JbooleanType));
  default:
    t->exception = makeIllegalArgumentException(t);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Class_initialize(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  initClass(t, *this_);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_java_lang_Class_isAssignableFrom(Thread* t, jobject this_, jclass that)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(that)) {
    return vm::isAssignableFrom(t, *this_, *that);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_reflect_Field_getPrimitive
(Thread* t, jclass, jobject instance, jint code, jint offset)
{
  ENTER(t, Thread::ActiveState);

  switch (code) {
  case ByteField: 
    return cast<int8_t>(*instance, offset);
  case BooleanField: 
    return cast<uint8_t>(*instance, offset);
  case CharField: 
    return cast<uint16_t>(*instance, offset);
  case ShortField: 
    return cast<int16_t>(*instance, offset);
  case IntField: 
    return cast<int32_t>(*instance, offset);
  case LongField: 
    return cast<int64_t>(*instance, offset);
  case FloatField: 
    return cast<uint32_t>(*instance, offset);
  case DoubleField: 
    return cast<uint64_t>(*instance, offset);
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_reflect_Field_getObject
(Thread* t, jclass, jobject instance, jint offset)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, cast<object>(*instance, offset));
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_reflect_Field_setPrimitive
(Thread* t, jclass, jobject instance, jint code, jint offset, jlong value)
{
  ENTER(t, Thread::ActiveState);

  switch (code) {
  case ByteField:
    cast<int8_t>(*instance, offset) = static_cast<int8_t>(value);
    break;
  case BooleanField:
    cast<uint8_t>(*instance, offset) = static_cast<uint8_t>(value);
    break;
  case CharField:
    cast<uint16_t>(*instance, offset) = static_cast<uint16_t>(value);
    break;
  case ShortField:
    cast<int16_t>(*instance, offset) = static_cast<int16_t>(value);
    break;
  case IntField: 
    cast<int32_t>(*instance, offset) = static_cast<int32_t>(value);
    break;
  case LongField: 
    cast<int64_t>(*instance, offset) = static_cast<int64_t>(value);
    break;
  case FloatField: 
    cast<uint32_t>(*instance, offset) = static_cast<uint32_t>(value);
    break;
  case DoubleField: 
    cast<uint64_t>(*instance, offset) = static_cast<uint64_t>(value);
    break;
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_reflect_Field_setObject
(Thread* t, jclass, jobject instance, jint offset, jobject value)
{
  ENTER(t, Thread::ActiveState);

  set(t, *instance, offset, (value ? *value : 0));
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_reflect_Constructor_make(Thread* t, jclass, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, make(t, *c));
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_reflect_Method_getCaller(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

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

  return makeLocalReference(t, v.method);
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_reflect_Method_invoke
(Thread* t, jclass, jobject method, jobject instance, jobjectArray arguments)
{
  ENTER(t, Thread::ActiveState);

  object v = t->m->processor->invokeArray
    (t, *method, (instance ? *instance : 0), *arguments);
  if (t->exception) {
    t->exception = makeInvocationTargetException(t, t->exception);
  }
  return makeLocalReference(t, v);
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_reflect_Array_getLength(Thread* t, jclass, jobject array)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(array)) {
    object a = *array;
    unsigned elementSize = classArrayElementSize(t, objectClass(t, a));

    if (LIKELY(elementSize)) {
      return cast<uintptr_t>(a, BytesPerWord);
    } else {
      t->exception = makeIllegalArgumentException(t);
    }
  } else {
    t->exception = makeNullPointerException(t);
  }
  return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_reflect_Array_makeObjectArray
(Thread* t, jclass, jclass elementType, jint length)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, makeObjectArray(t, *elementType, length, true));
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Float_floatToRawIntBits(Thread*, jclass, jfloat v)
{
  return floatToBits(v);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_java_lang_Float_intBitsToFloat(Thread*, jclass, jint v)
{
  return bitsToFloat(v);
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_Double_doubleToRawLongBits(Thread*, jclass, jdouble v)
{
  return doubleToBits(v);
}

extern "C" JNIEXPORT jdouble JNICALL
Java_java_lang_Double_longBitsToDouble(Thread*, jclass, jlong v)
{
  return bitsToDouble(v);
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_String_intern(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, intern(t, *this_));
}

extern "C" JNIEXPORT jstring JNICALL
Java_java_lang_System_getVMProperty(Thread* t, jclass, jstring name,
                                    jbooleanArray found)
{
  ENTER(t, Thread::ActiveState);

  unsigned length = stringLength(t, *name);
  char n[length + 1];
  stringChars(t, *name, n);

  jstring r = 0;
  if (strcmp(n, "java.lang.classpath") == 0) {
    r = makeLocalReference(t, makeString(t, "%s", t->m->finder->path()));
  } else if (strcmp(n, "avian.version") == 0) {
    r = makeLocalReference(t, makeString(t, AVIAN_VERSION));
  } else if (strcmp(n, "file.encoding") == 0) {
    r = makeLocalReference(t, makeString(t, "ASCII"));
  } else {
    const char* v = findProperty(t, n);
    if (v) {
      r = makeLocalReference(t, makeString(t, v));
    }
  }
  
  if (r) {
    booleanArrayBody(t, *found, 0) = true;
  }

  return r;
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_System_arraycopy
(Thread* t, jclass, jobject src, jint srcOffset,
 jobject dst, jint dstOffset, jint length)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(src and dst)) {
    object s = *src;
    object d = *dst;

    if (LIKELY(objectClass(t, s) == objectClass(t, d))) {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, s));

      if (LIKELY(elementSize)) {
        intptr_t sl = cast<uintptr_t>(s, BytesPerWord);
        intptr_t dl = cast<uintptr_t>(d, BytesPerWord);
        if (LIKELY(srcOffset >= 0 and srcOffset + length <= sl and
                   dstOffset >= 0 and dstOffset + length <= dl))
        {
          uint8_t* sbody = &cast<uint8_t>(s, ArrayBody);
          uint8_t* dbody = &cast<uint8_t>(d, ArrayBody);
          if (s == d) {
            memmove(dbody + (dstOffset * elementSize),
                    sbody + (srcOffset * elementSize),
                    length * elementSize);
          } else {
            memcpy(dbody + (dstOffset * elementSize),
                   sbody + (srcOffset * elementSize),
                   length * elementSize);
          }

          if (classObjectMask(t, objectClass(t, d))) {
            mark(t, d, ArrayBody + (dstOffset * BytesPerWord), length);
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

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_System_identityHashCode(Thread* t, jclass, jobject o)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(o)) {
    return objectHash(t, *o);
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Runtime_load(Thread* t, jclass, jstring name, jboolean mapName)
{
  ENTER(t, Thread::ActiveState);
  ACQUIRE(t, t->m->classLock);

  unsigned length = stringLength(t, *name);
  char n[length + 1];
  stringChars(t, *name, n);

  if (mapName and t->m->builtins) {
    const char* s = t->m->builtins;
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
Java_java_lang_Runtime_gc(Thread* t, jobject)
{
  collect(t, Heap::MajorCollection);
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Runtime_exit(Thread* t, jobject, jint code)
{
  ENTER(t, Thread::ActiveState);

  t->m->system->exit(code);
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_Runtime_freeMemory(Thread*, jobject)
{
  // todo
  return 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_Runtime_totalMemory(Thread*, jobject)
{
  // todo
  return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_Throwable_trace(Thread* t, jclass, jint skipCount)
{
  ENTER(t, Thread::ActiveState);

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

  if (v.trace == 0) v.trace = makeArray(t, 0, true);

  return makeLocalReference(t, v.trace);
}

extern "C" JNIEXPORT jarray JNICALL
Java_java_lang_Throwable_resolveTrace(Thread* t, jclass, jobject trace)
{
  ENTER(t, Thread::ActiveState);

  unsigned length = arrayLength(t, *trace);
  object array = makeObjectArray
    (t, arrayBody(t, t->m->types, Machine::StackTraceElementType),
     length, true);
  PROTECT(t, array);

  object e = 0;
  PROTECT(t, e);

  object class_ = 0;
  PROTECT(t, class_);

  for (unsigned i = 0; i < length; ++i) {
    e = arrayBody(t, *trace, i);

    class_ = className(t, methodClass(t, traceElementMethod(t, e)));
    class_ = makeString(t, class_, 0, byteArrayLength(t, class_) - 1, 0);

    object method = methodName(t, traceElementMethod(t, e));
    method = makeString(t, method, 0, byteArrayLength(t, method) - 1, 0);

    unsigned line = t->m->processor->lineNumber
      (t, traceElementMethod(t, e), traceElementIp(t, e));

    object ste = makeStackTraceElement(t, class_, method, 0, line);
    set(t, array, ArrayBody + (i * BytesPerWord), ste);
  }

  return makeLocalReference(t, array);
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_Thread_currentThread(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->javaThread);
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_lang_Thread_doStart(Thread* t, jobject this_)
{
  ENTER(t, Thread::ActiveState);

  Thread* p = t->m->processor->makeThread(t->m, *this_, t);

  if (t->m->system->success(t->m->system->start(&(p->runnable)))) {
    return reinterpret_cast<jlong>(p);
  } else {
    p->exit();
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_lang_Thread_interrupt(Thread* t, jclass, jlong peer)
{
  interrupt(t, reinterpret_cast<Thread*>(peer));
}

extern "C" JNIEXPORT jobject JNICALL
Java_java_lang_Thread_getStackTrace(Thread* t, jclass, jlong peer)
{
  ENTER(t, Thread::ActiveState);

  if (reinterpret_cast<Thread*>(peer) == t) {
    return makeLocalReference(t, makeTrace(t));
  } else {
    return makeLocalReference
      (t, t->m->processor->getStackTrace(t, reinterpret_cast<Thread*>(peer)));
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Thread_activeCount(Thread* t, jclass)
{
  return t->m->liveCount;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_lang_Thread_enumerate(Thread* t, jclass, jobjectArray array)
{
  ACQUIRE_RAW(t, t->m->stateLock);

  ENTER(t, Thread::ActiveState);

  unsigned count = min(t->m->liveCount, objectArrayLength(t, *array));
  unsigned index = 0;
  enumerateThreads(t, t->m->rootThread, *array, &index, count);
  return count;
}

extern "C" JNIEXPORT jint JNICALL
Java_java_net_URL_00024ResourceInputStream_getContentLength
(Thread* t, jclass, jstring path)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(path)) {
    char p[stringLength(t, *path) + 1];
    stringChars(t, *path, p);

    System::Region* r = t->m->finder->find(p);
    if (r) {
      jint rSize = r->length();
      r->dispose();
      return rSize;
    }
  }
  return -1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_java_net_URL_00024ResourceInputStream_open
(Thread* t, jclass, jstring path)
{
  ENTER(t, Thread::ActiveState);

  if (LIKELY(path)) {
    char p[stringLength(t, *path) + 1];
    stringChars(t, *path, p);

    return reinterpret_cast<jlong>(t->m->finder->find(p));
  } else {
    t->exception = makeNullPointerException(t);
    return 0;
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_java_net_URL_00024ResourceInputStream_read__JI
(Thread*, jclass, jlong peer, jint position)
{
  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (position >= static_cast<jint>(region->length())) {
    return -1;
  } else {
    return region->start()[position];
  }
}

extern "C" JNIEXPORT jint JNICALL
Java_java_net_URL_00024ResourceInputStream_read__JI_3BII
(Thread* t, jclass, jlong peer, jint position,
 jbyteArray b, jint offset, jint length)
{
  ENTER(t, Thread::ActiveState);

  if (length == 0) return 0;
  
  System::Region* region = reinterpret_cast<System::Region*>(peer);
  if (length > static_cast<jint>(region->length()) - position) {
    length = static_cast<jint>(region->length()) - position;
  }
  if (length <= 0) {
    return -1;
  } else {
    memcpy(&byteArrayBody(t, *b, offset), region->start() + position, length);
    return length;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_java_net_URL_00024ResourceInputStream_close(Thread*, jclass, jlong peer)
{
  reinterpret_cast<System::Region*>(peer)->dispose();
}
