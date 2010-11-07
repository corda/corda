/* Copyright (c) 2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef CLASSPATH_COMMON_H
#define CLASSPATH_COMMON_H

#include "tokenizer.h"

namespace vm {

object
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

bool
compatibleArrayTypes(Thread* t, object a, object b)
{
  return classArrayElementSize(t, a)
    and classArrayElementSize(t, b)
    and (a == b
         or (not ((classVmFlags(t, a) & PrimitiveFlag)
                  or (classVmFlags(t, b) & PrimitiveFlag))));
}

void
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
loadLibrary(Thread* t, const char* name)
{
  ACQUIRE(t, t->m->classLock);

  System::Library* last = t->m->libraries;
  for (System::Library* lib = t->m->libraries; lib; lib = lib->next()) {
    if (lib->name() and ::strcmp(lib->name(), name) == 0) {
      // already loaded
      return lib;
    }
    last = lib;
  }

  System::Library* lib;
  if (t->m->system->success(t->m->system->load(&lib, name))) {
    last->setNext(lib);
    return lib;
  } else {
    return 0;
  }
}

System::Library*
loadLibrary(Thread* t, const char* path, const char* name, bool mapName,
            bool runOnLoad)
{
  ACQUIRE(t, t->m->classLock);

  unsigned nameLength = strlen(name);
  if (mapName) {
    const char* builtins = findProperty(t, "avian.builtins");
    if (builtins) {
      const char* s = builtins;
      while (*s) {
        if (::strncmp(s, name, nameLength) == 0
            and (s[nameLength] == ',' or s[nameLength] == 0))
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

    const char* prefix = t->m->system->libraryPrefix();
    const char* suffix = t->m->system->librarySuffix();
    unsigned mappedNameLength = nameLength + strlen(prefix) + strlen(suffix);

    char* mappedName = static_cast<char*>
      (t->m->heap->allocate(mappedNameLength + 1));

    snprintf(mappedName, mappedNameLength + 1, "%s%s%s", prefix, name, suffix);

    name = mappedName;
    nameLength = mappedNameLength;
  }

  System::Library* lib = 0;
  for (Tokenizer tokenizer(path, t->m->system->pathSeparator());
       tokenizer.hasMore();)
  {
    Tokenizer::Token token(tokenizer.next());

    unsigned fullNameLength = token.length + 1 + nameLength;
    RUNTIME_ARRAY(char, fullName, fullNameLength + 1);

    snprintf(RUNTIME_ARRAY_BODY(fullName), fullNameLength + 1,
             "%*s/%s", token.length, token.s, name);

    lib = loadLibrary(t, RUNTIME_ARRAY_BODY(fullName));
    if (lib) break;
  }

  if (lib == 0) {
    lib = loadLibrary(t, name);
  }

  if (lib) {
    if (runOnLoad) {
      runOnLoadIfFound(t, lib);
    }
  } else {  
    object message = makeString(t, "library not found: %s", name);
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::UnsatisfiedLinkErrorType, message);
  }

  if (mapName) {
    t->m->heap->free(name, nameLength + 1);
  }

  return lib;
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
