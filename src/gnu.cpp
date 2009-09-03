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
#include "util.h"

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

namespace vm {

jobject JNICALL
NewDirectByteBuffer(Thread* t, void* address, jlong capacity)
{
  const char* pointerClassName;
  const char* initSpec;
  if (BytesPerWord == 8) {
    pointerClassName = "gnu/classpath/Pointer64";
    initSpec = "(J)V";
  } else {
    pointerClassName = "gnu/classpath/Pointer32";
    initSpec = "(I)V";
  }

  object pointerClass = resolveClass(t, t->m->loader, pointerClassName);
  if (UNLIKELY(pointerClass == 0)) return 0;
  PROTECT(t, pointerClass);

  object pointerConstructor = resolveMethod
    (t, pointerClass, "<init>", initSpec);
  if (UNLIKELY(pointerConstructor == 0)) return 0;

  object pointer = make(t, pointerClass);
  PROTECT(t, pointer);

  t->m->processor->invoke(t, pointerConstructor, pointer, address);
  if (UNLIKELY(t->exception)) return 0;

  object bufferClass = resolveClass
    (t, t->m->loader, "java/nio/DirectByteBufferImpl$ReadWrite");
  if (UNLIKELY(bufferClass == 0)) return 0;
  PROTECT(t, bufferClass);
  
  object bufferConstructor = resolveMethod
    (t, bufferClass, "<init>", "(Lgnu/classpath/Pointer;int)V");
  if (UNLIKELY(bufferConstructor == 0)) return 0;
  
  object buffer = make(t, bufferClass);
  PROTECT(t, buffer);

  t->m->processor->invoke
    (t, bufferConstructor, buffer, &pointer, static_cast<jint>(capacity));
  if (UNLIKELY(t->exception)) return 0;
  
  return makeLocalReference(t, buffer);
}

void* JNICALL
GetDirectBufferAddress(Thread* t, jobject buffer)
{
  object addressField = resolveField
    (t, objectClass(t, *buffer), "address", "Lgnu/classpath/Pointer;");
  if (UNLIKELY(addressField == 0)) return 0;

  object address = cast<object>(*buffer, fieldOffset(t, addressField));
  if (address == 0) return 0;
  
  const char* dataSpec;
  if (BytesPerWord == 8) {
    dataSpec = "J";
  } else {
    dataSpec = "I";
  }
  
  object dataField = resolveField
    (t, objectClass(t, address), "data", dataSpec);
  if (UNLIKELY(dataField == 0)) return 0;

  return cast<void*>(address, fieldOffset(t, dataField));
}

jlong JNICALL
GetDirectBufferCapacity(Thread* t, jobject buffer)
{
  object capField = resolveField(t, objectClass(t, *buffer), "cap", "I");
  if (UNLIKELY(capField == 0)) return 0;

  return cast<jint>(*buffer, fieldOffset(t, capField));
}

} // namespace vm

extern "C" JNIEXPORT void JNICALL
Avian_gnu_classpath_VMSystemProperties_preInit
(Thread* t, object, uintptr_t* arguments)
{
  object properties = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, properties);

  object method = resolveMethod
    (t, t->m->loader, "java/util/Properties", "setProperty",
     "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

  if (UNLIKELY(t->exception)) {
    return;
  }

  PROTECT(t, method);

  setProperty(t, method, properties, "java.version", "1.5");
  setProperty(t, method, properties, "java.specification.version", "1.5");

  setProperty(t, method, properties, "java.vm.name", "Avian");

  setProperty(t, method, properties, "java.protocol.handler.pkgs", "avian");

  setProperty(t, method, properties, "file.encoding", "ASCII");

  // specify a bogus library path so we can do our own search in
  // VMRuntime.nativeLoad:
#define LIBRARY_PATH_SENTINAL "*"
  setProperty(t, method, properties, "java.library.path",
              LIBRARY_PATH_SENTINAL);

#ifdef PLATFORM_WINDOWS
#  define FILE_SEPARATOR "\\"
  
  setProperty(t, method, properties, "line.separator", "\r\n");
  setProperty(t, method, properties, "file.separator", FILE_SEPARATOR);
  setProperty(t, method, properties, "path.separator", ";");
  setProperty(t, method, properties, "os.name", "Windows");

  TCHAR buffer[MAX_PATH];
  GetTempPath(MAX_PATH, buffer);
  setProperty(t, method, properties, "java.io.tmpdir", buffer);

  setProperty(t, method, properties, "user.home",
              _wgetenv(L"USERPROFILE"), "%ls");

  GetCurrentDirectory(MAX_PATH, buffer);
  setProperty(t, method, properties, "user.dir", buffer);
#else
#  define FILE_SEPARATOR "/"
  
  setProperty(t, method, properties, "line.separator", "\n");
  setProperty(t, method, properties, "file.separator", FILE_SEPARATOR);
  setProperty(t, method, properties, "path.separator", ":");
#  ifdef __APPLE__
  setProperty(t, method, properties, "os.name", "Mac OS X");
#  else
  setProperty(t, method, properties, "os.name", "Linux");
#  endif
  setProperty(t, method, properties, "java.io.tmpdir", "/tmp");
  setProperty(t, method, properties, "user.home", getenv("HOME"));
  setProperty(t, method, properties, "user.dir", getenv("PWD"));
#endif

#ifdef ARCH_x86_32
  setProperty(t, method, properties, "gnu.cpu.endian", "little");
  setProperty(t, method, properties, "os.arch", "x86");
#elif defined ARCH_x86_64
  setProperty(t, method, properties, "gnu.cpu.endian", "little");
  setProperty(t, method, properties, "os.arch", "x86_64");
#elif defined ARCH_powerpc
  setProperty(t, method, properties, "gnu.cpu.endian", "big");
  setProperty(t, method, properties, "os.arch", "ppc");
#elif defined ARCH_arm
  setProperty(t, method, properties, "os.arch", "arm");
#else
  setProperty(t, method, properties, "os.arch", "unknown");
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
            (t, t->m->loader, arrayBody(t, t->m->types, Machine::ClassType),
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
      (t, t->m->loader, arrayBody(t, t->m->types, Machine::ClassType), 0);
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

  const unsigned soPrefixLength = sizeof(SO_PREFIX) - 1;
  const unsigned nameLength = stringLength(t, name);
  const unsigned soSuffixLength = sizeof(SO_SUFFIX) - 1;
  const unsigned total = soPrefixLength + nameLength + soSuffixLength;

  object s = makeByteArray(t, total + 1);
  char* p = reinterpret_cast<char*>(&byteArrayBody(t, s, 0));

  memcpy(p, SO_PREFIX, soPrefixLength);
  stringChars(t, name, p + soPrefixLength);
  memcpy(p + soPrefixLength + nameLength, SO_SUFFIX, soSuffixLength);
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
  object name = reinterpret_cast<object>(arguments[0]);

  // given that we set java.library.path to LIBRARY_PATH_SENTINAL, we
  // can determine which names are filenames and which are library
  // names by looking for the prefix LIBRARY_PATH_SENTINAL
  // FILE_SEPARATOR

  unsigned length = stringLength(t, name);
  char n[length + 1];
  stringChars(t, name, n);

  const unsigned pathPrefixLength
    = sizeof(LIBRARY_PATH_SENTINAL) - 1
    + sizeof(FILE_SEPARATOR) - 1;

  bool mapName = (strncmp(n, LIBRARY_PATH_SENTINAL FILE_SEPARATOR,
                          pathPrefixLength) == 0);
  if (mapName) {
    // strip the path prefix, SO prefix, and SO suffix before passing
    // the name to Runtime.load

    const unsigned soPrefixLength = sizeof(SO_PREFIX) - 1;
    const unsigned soSuffixLength = sizeof(SO_SUFFIX) - 1;
    const unsigned newOffset
      = stringOffset(t, name) + pathPrefixLength + soPrefixLength;
    const unsigned newLength
      = length - pathPrefixLength - soPrefixLength - soSuffixLength;

    name = makeString(t, stringData(t, name), newOffset, newLength, 0);
  }

  uintptr_t args[] = { reinterpret_cast<uintptr_t>(name), mapName };

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
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_getPrimitiveClass
(Thread* t, object, uintptr_t* arguments)
{
  return Avian_java_lang_Class_primitiveClass(t, 0, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_ClassLoader_defineClass
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_defineClass
(Thread* t, object, uintptr_t* arguments)
{
  uintptr_t args[]
    = { arguments[0], arguments[2], arguments[3], arguments[4] };

//   object name = reinterpret_cast<object>(arguments[1]);
//   char n[stringLength(t, name) + 1];
//   stringChars(t, name, n);
//   fprintf(stderr, "define class %s in %p\n", n,
//           reinterpret_cast<void*>(arguments[0]));

  return Avian_java_lang_ClassLoader_defineClass(t, 0, args);
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

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_VMRuntime_runFinalizationForExit
(Thread*, object, uintptr_t*)
{
  // ignore
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_VMRuntime_exit
(Thread*, object, uintptr_t* arguments)
{
  exit(arguments[0]);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_avian_SystemClassLoader_findClass
(Thread*, object, uintptr_t*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_loadClass
(Thread* t, object, uintptr_t* arguments)
{
  uintptr_t args[] = { 0, arguments[0] };

//   object name = reinterpret_cast<object>(arguments[0]);
//   char n[stringLength(t, name) + 1];
//   stringChars(t, name, n);
//   fprintf(stderr, "load bootstrap class %s in %p\n", n, t->m->loader);

  return Avian_avian_SystemClassLoader_findClass(t, 0, args);
}

extern "C" JNIEXPORT void JNICALL
Avian_java_lang_VMClassLoader_resolveClass
(Thread*, object, uintptr_t*)
{
  // ignore
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_VMClassLoader_findLoadedClass
(Thread* t, object, uintptr_t* arguments)
{
  object loader = reinterpret_cast<object>(arguments[0]);
  
  object map = getClassLoaderMap(t, loader);
  if (map) {
    PROTECT(t, loader);

    object name = reinterpret_cast<object>(arguments[1]);
    PROTECT(t, name);

    object n = makeByteArray(t, stringLength(t, name) + 1);
    char* s = reinterpret_cast<char*>(&byteArrayBody(t, n, 0));
    stringChars(t, name, s);
    
    replace('.', '/', s);

    return reinterpret_cast<int64_t>
      (hashMapFind
       (t, getClassLoaderMap(t, loader), n, byteArrayHash, byteArrayEqual));
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapInt
(Thread*, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int32_t expect = arguments[4];
  int32_t update = arguments[5];

  return __sync_bool_compare_and_swap
    (&cast<int32_t>(target, offset), expect, update);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapLong
(Thread*, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int64_t expect; memcpy(&expect, arguments + 4, 8);
  int64_t update; memcpy(&update, arguments + 6, 8);

  return __sync_bool_compare_and_swap
    (&cast<int64_t>(target, offset), expect, update);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_objectFieldOffset
(Thread* t, object, uintptr_t* arguments)
{
  return fieldOffset(t, reinterpret_cast<object>(arguments[1]));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_util_concurrent_atomic_AtomicLong_VMSupportsCS8
(Thread*, object, uintptr_t*)
{
  return 0;
}
