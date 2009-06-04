/* Copyright (c) 2009, Avian Contributors

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

void
setProperty(Thread* t, object method, object properties,
            const char* name, const void* value, const char* format = "%s")
{
  PROTECT(t, method);
  PROTECT(t, properties);
  
  object n = makeString(t, "%s", name);
  PROTECT(t, n);

  object v = makeString(t, format, value);

  t->m->processor->invoke(t, method, properties, n, v);
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Avian_gnu_classpath_VMSystemProperties_preInit
(Thread* t, object, uintptr_t* arguments)
{
  object properties = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, properties);

  object method = resolveMethod
    (t, "java/util/Properties", "setProperty",
     "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

  if (UNLIKELY(t->exception)) {
    return;
  }

  PROTECT(t, method);

  setProperty(t, method, properties, "java.vm.name", "Avian");

  setProperty(t, method, properties, "java.lang.classpath",
              t->m->finder->path());

  setProperty(t, method, properties, "file.encoding", "ASCII");

#ifdef WIN32 
  setProperty(t, method, properties, "line.separator", "\r\n");
  setProperty(t, method, properties, "file.separator", "\\");
  setProperty(t, method, properties, "path.separator", ";");
  setProperty(t, method, properties, "os.name", "Windows");

  TCHAR buffer[MAX_PATH];
  GetTempPath(MAX_PATH, buffer);
  setProperty(t, method, properties, "java.io.tmpdir", buffer);

  setProperty(t, method, properties, "user.home",
              _wgetenv(L"USERPROFILE"), "%ls");

  setProperty(t, method, properties, "java.library.path",
              _wgetenv(L"PATH"), "%ls");
#else
  setProperty(t, method, properties, "line.separator", "\n");
  setProperty(t, method, properties, "file.separator", "/");
  setProperty(t, method, properties, "path.separator", ":");
#  ifdef __APPLE__
  setProperty(t, method, properties, "os.name", "Mac OS X");
  setProperty(t, method, properties, "java.library.path",
              getenv("DYLD_LIBRARY_PATH"));
#  else
  setProperty(t, method, properties, "os.name", "Linux");
  setProperty(t, method, properties, "java.library.path",
              getenv("LD_LIBRARY_PATH"));
#  endif
  setProperty(t, method, properties, "java.io.tmpdir", "/tmp");
  setProperty(t, method, properties, "user.home", getenv("HOME"));
#endif
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_gnu_classpath_VMStackWalker_getClassContext
(Thread* t, object, uintptr_t*)
{
  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t):
      t(t), skipCount(1), trace(0), index(0), protector(t, &trace)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      if (skipCount == 0) {
        if (trace == 0) {
          trace = makeObjectArray
            (t, arrayBody(t, t->m->types, Machine::ClassType),
             walker->count());
        }

        assert(t, index < objectArrayLength(t, trace));

        set(t, trace, ArrayBody + (index * BytesPerWord),
            methodClass(t, walker->method()));

        ++ index;
        return true;
      } else {
        -- skipCount;
        return true;
      }
    }

    Thread* t;
    unsigned skipCount;
    object trace;
    unsigned index;
    Thread::SingleProtector protector;
  } v(t);

  t->m->processor->walkStack(t, &v);

  if (v.trace == 0) {
    v.trace = makeObjectArray
      (t, arrayBody(t, t->m->types, Machine::ClassType), 0);
  }

  return reinterpret_cast<int64_t>(v.trace);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_gnu_classpath_VMStackWalker_getClassLoader
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>
    (classLoader(t, reinterpret_cast<object>(arguments[0])));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMRuntime_mapLibraryName
(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  const unsigned prefixLength = sizeof(SO_PREFIX) - 1;
  const unsigned nameLength = stringLength(t, name);
  const unsigned suffixLength = sizeof(SO_SUFFIX) - 1;
  const unsigned total = prefixLength + nameLength + suffixLength;

  object s = makeByteArray(t, total + 1);
  char* p = reinterpret_cast<char*>(&byteArrayBody(t, s, 0));

  memcpy(p, SO_PREFIX, prefixLength);
  stringChars(t, name, p + prefixLength);
  memcpy(p + prefixLength + nameLength, SO_SUFFIX, suffixLength);
  p[total] = 0;

  return reinterpret_cast<int64_t>(makeString(t, s, 0, total, 0));
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_System_arraycopy
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_VMSystem_arraycopy
(Thread* t, object, uintptr_t* arguments)
{
  Avian_java_lang_System_arraycopy(t, 0, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_load
(Thread* t, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMRuntime_nativeLoad
(Thread* t, object, uintptr_t* arguments)
{
  uintptr_t args[] = { arguments[0], 0 };

  Avian_java_lang_Runtime_load(t, 0, args);

  if (t->exception) {
    t->exception = 0;
    return 0;
  } else {
    return 1;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_primitiveClass
(Thread* t, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_getPrimitiveClass
(Thread* t, object, uintptr_t* arguments)
{
  return Avian_java_lang_Class_primitiveClass(t, 0, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_System_identityHashCode
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMSystem_identityHashCode
(Thread* t, object, uintptr_t* arguments)
{
  return Avian_java_lang_System_identityHashCode(t, 0, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_Runtime_gc
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_VMRuntime_gc
(Thread* t, object, uintptr_t*)
{
  Avian_java_lang_Runtime_gc(t, 0, 0);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findClass
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_loadClass
(Thread* t, object, uintptr_t* arguments)
{
  uintptr_t args[] = { 0, arguments[0] };

  return Avian_avian_SystemClassLoader_findClass(t, 0, args);
}
