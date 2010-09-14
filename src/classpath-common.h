/* Copyright (c) 2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef CLASSPATH_COMMON_H
#define CLASSPATH_COMMON_H

namespace vm {

inline object
getCaller(Thread* t, unsigned target)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, unsigned target):
      t(t), method(0), count(0), target(target)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
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
  } v(t, target);

  t->m->processor->walkStack(t, &v);

  return v.method;
}

inline object
getTrace(Thread* t, unsigned skipCount)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t, int skipCount):
      t(t), trace(0), skipCount(skipCount)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipCount == 0) {
        object method = walker->method();
        if (isAssignableFrom
            (t, type(t, Machine::ThrowableType), methodClass(t, method))
            and vm::strcmp(reinterpret_cast<const int8_t*>("<init>"),
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

  if (v.trace == 0) v.trace = makeObjectArray(t, 0);

  return v.trace;
}

inline bool
compatibleArrayTypes(Thread* t, object a, object b)
{
  return classArrayElementSize(t, a)
    and classArrayElementSize(t, b)
    and (a == b
         or (not ((classVmFlags(t, a) & PrimitiveFlag)
                  or (classVmFlags(t, b) & PrimitiveFlag))));
}

inline void
arrayCopy(Thread* t, object src, int32_t srcOffset, object dst,
          int32_t dstOffset, int32_t length)
{
  if (LIKELY(src and dst)) {
    if (LIKELY(compatibleArrayTypes
               (t, objectClass(t, src), objectClass(t, dst))))
    {
      unsigned elementSize = classArrayElementSize(t, objectClass(t, src));

      if (LIKELY(elementSize)) {
        intptr_t sl = cast<uintptr_t>(src, BytesPerWord);
        intptr_t dl = cast<uintptr_t>(dst, BytesPerWord);
        if (LIKELY(length > 0)) {
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
          } else {
            t->exception = t->m->classpath->makeThrowable
              (t, Machine::IndexOutOfBoundsExceptionType);
            return;
          }
        } else {
          return;
        }
      }
    }
  } else {
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::NullPointerExceptionType);
    return;
  }

  t->exception = t->m->classpath->makeThrowable
    (t, Machine::ArrayStoreExceptionType);
}

void
runOnLoadIfFound(Thread* t, System::Library* library)
{
  void* p = library->resolve("JNI_OnLoad");
  if (p) {
    jint (JNICALL * JNI_OnLoad)(Machine*, void*);
    memcpy(&JNI_OnLoad, &p, sizeof(void*));
    JNI_OnLoad(t->m, 0);
  }
}

System::Library*
loadLibrary(Thread* t, const char* name, bool mapName, bool runOnLoad)
{
  ACQUIRE(t, t->m->classLock);

  const char* builtins = findProperty(t, "avian.builtins");
  if (mapName and builtins) {
    const char* s = builtins;
    while (*s) {
      unsigned length = strlen(name);
      if (::strncmp(s, name, length) == 0
          and (s[length] == ',' or s[length] == 0))
      {
        // library is built in to this executable
        if (runOnLoad and not t->m->triedBuiltinOnLoad) {
          t->m->triedBuiltinOnLoad = true;
          runOnLoadIfFound(t, t->m->libraries);
        }
        return t->m->libraries;
      } else {
        while (*s and *s != ',') ++ s;
        if (*s) ++ s;
      }
    }
  }

  System::Library* last = t->m->libraries;
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    if (lib->name()
        and ::strcmp(lib->name(), name) == 0
        and lib->mapName() == mapName)
    {
      // already loaded
      return lib;
    }
    last = lib;
  }

  System::Library* lib;
  if (LIKELY(t->m->system->success(t->m->system->load(&lib, name, mapName)))) {
    last->setNext(lib);
    if (runOnLoad) {
      runOnLoadIfFound(t, lib);
    }
    return lib;
  } else {
    object message = makeString(t, "library not found: %s", name);
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::UnsatisfiedLinkErrorType, message);
    return 0;
  }
}

object
clone(Thread* t, object o)
{
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

  return clone;
}

object
makeStackTraceElement(Thread* t, object e)
{
  PROTECT(t, e);

  object class_ = className(t, methodClass(t, traceElementMethod(t, e)));
  PROTECT(t, class_);

  RUNTIME_ARRAY(char, s, byteArrayLength(t, class_));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, class_, 0)));
  class_ = makeString(t, "%s", s);

  object method = methodName(t, traceElementMethod(t, e));
  PROTECT(t, method);

  method = t->m->classpath->makeString
    (t, method, 0, byteArrayLength(t, method) - 1);

  unsigned line = t->m->processor->lineNumber
    (t, traceElementMethod(t, e), traceElementIp(t, e));

  object file = classSourceFile(t, methodClass(t, traceElementMethod(t, e)));
  file = file ? t->m->classpath->makeString
    (t, file, 0, byteArrayLength(t, file) - 1) : 0;

  return makeStackTraceElement(t, class_, method, file, line);
}

} // namespace vm

#endif//CLASSPATH_COMMON_H
