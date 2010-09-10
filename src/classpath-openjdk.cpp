/* Copyright (c) 2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"
#include "classpath-common.h"
#include "util.h"

// todo: move platform-specific stuff into system.h and implementations

#ifdef PLATFORM_WINDOWS

#  include <windows.h>
#  include <io.h>
#  include <direct.h>
#  include <share.h>

#  define CLOSE _close
#  define READ _read
#  define WRITE _write

#  ifdef _MSC_VER
#    define S_ISREG(x) ((x) | _S_IFREG)
#    define S_ISDIR(x) ((x) | _S_IFDIR)
#    define S_IRUSR _S_IREAD
#    define S_IWUSR _S_IWRITE
#  else
#    define OPEN _wopen
#    define CREAT _wcreat
#  endif

#else // not PLATFORM_WINDOWS

#  include <unistd.h>
#  include <sys/types.h>
#  include <sys/stat.h>
#  include <fcntl.h>
#  include <errno.h>

#  define OPEN open
#  define CLOSE close
#  define READ read
#  define WRITE write
#  define LSEEK lseek

#endif // not PLATFORM_WINDOWS

using namespace vm;

namespace {

#ifdef _MSC_VER
inline int 
OPEN(string_t path, int mask, int mode)
{
  int fd; 
  if (_wsopen_s(&fd, path, mask, _SH_DENYNO, mode) == 0) {
    return fd; 
  } else {
    return -1; 
  }
}

inline int
CREAT(string_t path, int mode)
{
  return OPEN(path, _O_CREAT, mode);
}
#endif

namespace local {

const unsigned InterfaceVersion = 4;

Machine* globalMachine;


const char*
primitiveName(Thread* t, object c)
{
  if (c == primitiveClass(t, 'V')) {
    return "void";
  } else if (c == primitiveClass(t, 'Z')) {
    return "boolean";
  } else if (c == primitiveClass(t, 'B')) {
    return "byte";
  } else if (c == primitiveClass(t, 'C')) {
    return "char";
  } else if (c == primitiveClass(t, 'S')) {
    return "short";
  } else if (c == primitiveClass(t, 'I')) {
    return "int";
  } else if (c == primitiveClass(t, 'F')) {
    return "float";
  } else if (c == primitiveClass(t, 'J')) {
    return "long";
  } else if (c == primitiveClass(t, 'D')) {
    return "double";
  } else {
    abort(t);
  }
}

object
getClassName(Thread* t, object c)
{
  if (className(t, c) == 0) {
    if (classVmFlags(t, c) & PrimitiveFlag) {
      PROTECT(t, c);
      
      object name = makeByteArray(t, primitiveName(t, c));

      set(t, c, ClassName, name);
    } else {
      abort(t);
    }
  }

  return className(t, c);
}

object
makeClassNameString(Thread* t, object name)
{
  RUNTIME_ARRAY(char, s, byteArrayLength(t, name));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, name, 0)));

  return makeString(t, "%s", s);
}

class MyClasspath : public Classpath {
 public:
  MyClasspath(Allocator* allocator):
    allocator(allocator)
  { }

  virtual object
  makeJclass(Thread* t, object class_)
  {
    object name = makeClassNameString(t, getClassName(t, class_));

    return vm::makeJclass
      (t, 0, 0, name, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, class_);
  }

  virtual object
  makeString(Thread* t, object array, int32_t offset, int32_t length)
  {
    if (objectClass(t, array)
        == arrayBody(t, t->m->types, Machine::ByteArrayType))
    {
      PROTECT(t, array);
      
      object charArray = makeCharArray(t, length);
      for (int i = 0; i < length; ++i) {
        charArrayBody(t, charArray, i) = byteArrayBody(t, array, offset + i);
      }

      array = charArray;
    }
    return vm::makeString(t, array, offset, length, 0);
  }

  virtual object
  makeThread(Thread* t, Thread* parent)
  {
    const unsigned MaxPriority = 10;
    const unsigned NormalPriority = 5;

    object group;
    if (parent) {
      group = threadGroup(t, parent->javaThread);
    } else {
      group = makeThreadGroup
        (t, 0, 0, MaxPriority, false, false, false, 0, 0, 0, 0, 0);
    }

    return vm::makeThread
      (t, 0, NormalPriority, 0, 0, false, false, false, 0, group, t->m->loader,
       0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, false);
  }

  virtual void
  runThread(Thread* t)
  {
    object method = resolveMethod
      (t, t->m->loader, "java/lang/Thread", "run", "()V");

    if (LIKELY(t->exception == 0)) {
      t->m->processor->invoke(t, method, t->javaThread);
    }
  }

  virtual object
  makeThrowable
  (Thread* t, Machine::Type type, object message, object trace, object cause)
  {
    PROTECT(t, message);
    PROTECT(t, cause);
    
    if (trace == 0) {
      trace = makeTrace(t);
    }

    object result = make(t, arrayBody(t, t->m->types, type));
    
    set(t, result, ThrowableMessage, message);
    set(t, result, ThrowableTrace, trace);
    set(t, result, ThrowableCause, cause);

    return result;
  }

  virtual void
  boot(Thread* t)
  {
    globalMachine = t->m;

    if (loadLibrary(t, "java", true, true) == 0) {
      abort(t);
    }

    t->m->processor->invoke
      (t, t->m->loader, "java/lang/System", "initializeSystemClass", "()V", 0);
  }

  virtual void
  dispose()
  {
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
};

struct JVM_ExceptionTableEntryType{
    jint start_pc;
    jint end_pc;
    jint handler_pc;
    jint catchType;
};

struct jvm_version_info {
    unsigned int jvm_version;
    unsigned int update_version: 8;
    unsigned int special_update_version: 8;
    unsigned int reserved1: 16;
    unsigned int reserved2;
    unsigned int is_attach_supported: 1;
    unsigned int is_kernel_jvm: 1;
    unsigned int: 30;
    unsigned int: 32;
    unsigned int: 32;
};

unsigned
countMethods(Thread* t, object c, bool publicOnly)
{
  object table = classMethodTable(t, c);
  unsigned count = 0;
  for (unsigned i = 0; i < arrayLength(t, table); ++i) {
    object vmMethod = arrayBody(t, table, i);
    if (((not publicOnly) or (methodFlags(t, vmMethod) & ACC_PUBLIC))
        and byteArrayBody(t, methodName(t, vmMethod), 0) != '<')
    {
      ++ count;
    }
  }
  return count;
}

unsigned
countFields(Thread* t, object c, bool publicOnly)
{
  object table = classFieldTable(t, c);
  if (publicOnly) {
    return objectArrayLength(t, table);
  } else {
    unsigned count = 0;
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object vmField = arrayBody(t, table, i);
      if ((not publicOnly) or (fieldFlags(t, vmField) & ACC_PUBLIC)) {
        ++ count;
      }
    }
    return count;
  }
}

unsigned
countConstructors(Thread* t, object c, bool publicOnly)
{
  object table = classMethodTable(t, c);
  unsigned count = 0;
  for (unsigned i = 0; i < arrayLength(t, table); ++i) {
    object vmMethod = arrayBody(t, table, i);
    if (((not publicOnly) or (methodFlags(t, vmMethod) & ACC_PUBLIC))
        and strcmp(reinterpret_cast<char*>
                   (&byteArrayBody(t, methodName(t, vmMethod), 0)),
                   "<init>") == 0)
    {
      ++ count;
    }
  }
  return count;
}

object
resolveClassBySpec(Thread* t, object loader, const char* spec,
                   unsigned specLength)
{
  switch (*spec) {
  case 'L': {
    RUNTIME_ARRAY(char, s, specLength - 1);
    memcpy(RUNTIME_ARRAY_BODY(s), spec + 1, specLength - 2);
    RUNTIME_ARRAY_BODY(s)[specLength - 2] = 0;
    return resolveClass(t, loader, s);
  }
  
  case '[': {
    RUNTIME_ARRAY(char, s, specLength + 1);
    memcpy(RUNTIME_ARRAY_BODY(s), spec, specLength);
    RUNTIME_ARRAY_BODY(s)[specLength] = 0;
    return resolveClass(t, loader, s);
  }

  default:
    return primitiveClass(t, *spec);
  }
}

object
resolveJType(Thread* t, object loader, const char* spec, unsigned specLength)
{
  object c = resolveClassBySpec(t, loader, spec, specLength);
  
  if (UNLIKELY(t->exception)) return 0;

  return getJClass(t, c);
}

object
resolveParameterTypes(Thread* t, object loader, object spec,
                      unsigned* parameterCount, unsigned* returnTypeSpec)
{
  PROTECT(t, loader);
  PROTECT(t, spec);

  object list = 0;
  PROTECT(t, list);

  unsigned offset = 1;
  unsigned count = 0;
  while (byteArrayBody(t, spec, offset) != ')') {
    switch (byteArrayBody(t, spec, offset)) {
    case 'L': {
      unsigned start = offset;
      ++ offset;
      while (byteArrayBody(t, spec, offset) != ';') ++ offset;
      ++ offset;

      object type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);
      if (UNLIKELY(t->exception)) {
        return 0;
      }
      
      list = makePair(t, type, list);

      ++ count;
    } break;
  
    case '[': {
      unsigned start = offset;
      while (byteArrayBody(t, spec, offset) == '[') ++ offset;
      switch (byteArrayBody(t, spec, offset)) {
      case 'L':
        ++ offset;
        while (byteArrayBody(t, spec, offset) != ';') ++ offset;
        ++ offset;
        break;

      default:
        ++ offset;
        break;
      }
      
      object type = resolveClassBySpec
        (t, loader, reinterpret_cast<char*>(&byteArrayBody(t, spec, start)),
         offset - start);
      if (UNLIKELY(t->exception)) {
        return 0;
      }
      
      list = makePair(t, type, list);
      ++ count;
    } break;

    default:
      list = makePair
        (t, primitiveClass(t, byteArrayBody(t, spec, offset)), list);
      ++ offset;
      ++ count;
      break;
    }
  }

  *parameterCount = count;
  *returnTypeSpec = offset + 1;
  return list;
}

object
resolveParameterJTypes(Thread* t, object loader, object spec,
                       unsigned* parameterCount, unsigned* returnTypeSpec)
{
  object list = resolveParameterTypes
    (t, loader, spec, parameterCount, returnTypeSpec);

  if (UNLIKELY(t->exception)) return 0;

  PROTECT(t, list);
  
  object array = makeObjectArray
    (t, t->m->loader, arrayBody(t, t->m->types, Machine::JclassType),
     *parameterCount);
  PROTECT(t, array);

  for (int i = *parameterCount - 1; i >= 0; --i) {
    object c = getJClass(t, pairFirst(t, list));
    set(t, array, ArrayBody + (i * BytesPerWord), c);
    list = pairSecond(t, list);
  }

  return array;
}

object
resolveExceptionJTypes(Thread* t, object loader, object addendum)
{
  if (addendum == 0) {
    return makeObjectArray
      (t, loader, arrayBody(t, t->m->types, Machine::JclassType), 0);
  }

  PROTECT(t, loader);
  PROTECT(t, addendum);

  object array = makeObjectArray
    (t, loader, arrayBody(t, t->m->types, Machine::JclassType),
     shortArrayLength(t, methodAddendumExceptionTable(t, addendum)));
  PROTECT(t, array);

  for (unsigned i = 0; i < shortArrayLength
         (t, methodAddendumExceptionTable(t, addendum)); ++i)
  {
    uint16_t index = shortArrayBody
      (t, methodAddendumExceptionTable(t, addendum), i) - 1;

    object o = singletonObject(t, addendumPool(t, addendum), index);

    if (objectClass(t, o)
        == arrayBody(t, t->m->types, Machine::ReferenceType))
    {
      o = resolveClass(t, loader, referenceName(t, o));
      if (UNLIKELY(t->exception)) return 0;
    
      set(t, addendumPool(t, addendum), SingletonBody + (index * BytesPerWord),
          o);
    }

    o = getJClass(t, o);

    set(t, array, ArrayBody + (i * BytesPerWord), o);
  }

  return array;
}

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

} // namespace local

} // namespace

namespace vm {

Classpath*
makeClasspath(System*, Allocator* allocator)
{
  return new (allocator->allocate(sizeof(local::MyClasspath)))
    local::MyClasspath(allocator);
}

} // namespace vm

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_getSuperclass
(Thread* t, object, uintptr_t* arguments)
{
  object super = classSuper
    (t, jclassVmClass(t, reinterpret_cast<object>(arguments[0])));

  return super ? reinterpret_cast<int64_t>(getJClass(t, super)) : 0;
}

extern "C" JNIEXPORT void
Avian_sun_misc_Unsafe_registerNatives
(Thread*, object, uintptr_t*)
{
  // ignore
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_staticFieldOffset
(Thread* t, object, uintptr_t* arguments)
{
  object jfield = reinterpret_cast<object>(arguments[1]);
  return fieldOffset
    (t, arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, jfieldClazz(t, jfield))), jfieldSlot(t, jfield)));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_staticFieldBase
(Thread* t, object, uintptr_t* arguments)
{
  return reinterpret_cast<int64_t>
    (classStaticTable
     (t, jclassVmClass
      (t, jfieldClazz(t, reinterpret_cast<object>(arguments[1])))));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_objectFieldOffset
(Thread* t, object, uintptr_t* arguments)
{
  object jfield = reinterpret_cast<object>(arguments[1]);
  return fieldOffset
    (t, arrayBody
     (t, classFieldTable
      (t, jclassVmClass(t, jfieldClazz(t, jfield))), jfieldSlot(t, jfield)));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getObjectVolatile
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  unsigned offset = arguments[2];
  object value = cast<object>(o, offset);
  loadMemoryBarrier();
  return reinterpret_cast<int64_t>(value);
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
Avian_sun_misc_Unsafe_allocateMemory
(Thread* t, object, uintptr_t* arguments)
{
  void* p = malloc(arguments[1]);
  if (p) {
    return reinterpret_cast<int64_t>(p);
  } else {
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::OutOfMemoryErrorType);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_freeMemory
(Thread*, object, uintptr_t* arguments)
{
  void* p = reinterpret_cast<void*>(arguments[1]);
  if (p) {
    free(p);
  }
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putLong__JJ
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int64_t v; memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<int64_t*>(p) = v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getByte__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int8_t*>(p);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_ensureClassInitialized
(Thread* t, object, uintptr_t* arguments)
{
  initClass(t, jclassVmClass(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" JNIEXPORT jint JNICALL
JVM_GetInterfaceVersion()
{
  return local::InterfaceVersion;
}

extern "C" JNIEXPORT jint JNICALL
JVM_IHashCode(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return objectHash(t, *o);
}

extern "C" JNIEXPORT void JNICALL
JVM_MonitorWait(Thread* t, jobject o, jlong milliseconds)
{
  ENTER(t, Thread::ActiveState);

  vm::wait(t, *o, milliseconds);
}

extern "C" JNIEXPORT void JNICALL
JVM_MonitorNotify(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  notify(t, *o);
}

extern "C" JNIEXPORT void JNICALL
JVM_MonitorNotifyAll(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  notifyAll(t, *o);
}

extern "C" JNIEXPORT jobject JNICALL
JVM_Clone(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, clone(t, *o));
}

extern "C" JNIEXPORT jstring JNICALL
JVM_InternString(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, intern(t, *s));
}

extern "C" JNIEXPORT jlong JNICALL
JVM_CurrentTimeMillis(Thread* t, jclass)
{
  return t->m->system->now();
}

extern "C" JNIEXPORT jlong JNICALL
JVM_NanoTime(Thread* t, jclass)
{
  return t->m->system->now() * 1000 * 1000;
}

extern "C" JNIEXPORT void JNICALL
JVM_ArrayCopy(Thread* t, jclass, jobject src, jint srcOffset, jobject dst,
              jint dstOffset, jint length)
{
  ENTER(t, Thread::ActiveState);

  arrayCopy(t, *src, srcOffset, *dst, dstOffset, length);
}

extern "C" JNIEXPORT jobject JNICALL
JVM_InitProperties(Thread* t, jobject properties)
{
  ENTER(t, Thread::ActiveState);

  object method = resolveMethod
    (t, t->m->loader, "java/util/Properties", "setProperty",
     "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

  if (UNLIKELY(t->exception)) {
    return 0;
  }

  PROTECT(t, method);

#ifdef PLATFORM_WINDOWS
  local::setProperty(t, method, *properties, "line.separator", "\r\n");
  local::setProperty(t, method, *properties, "file.separator", "\\");
  local::setProperty(t, method, *properties, "path.separator", ";");
  local::setProperty(t, method, *properties, "os.name", "Windows");

  TCHAR buffer[MAX_PATH];
  GetTempPath(MAX_PATH, buffer);

  local::setProperty(t, method, *properties, "java.io.tmpdir", buffer);
  local::setProperty(t, method, *properties, "java.home", buffer);
  local::setProperty(t, method, *properties, "user.home",
                     _wgetenv(L"USERPROFILE"), "%ls");

  GetCurrentDirectory(MAX_PATH, buffer);

  local::setProperty(t, method, *properties, "user.dir", buffer);
#else
  local::setProperty(t, method, *properties, "line.separator", "\n");
  local::setProperty(t, method, *properties, "file.separator", "/");
  local::setProperty(t, method, *properties, "path.separator", ":");
#  ifdef __APPLE__
  local::setProperty(t, method, *properties, "os.name", "Mac OS X");
#  else
  local::setProperty(t, method, *properties, "os.name", "Linux");
#  endif
  local::setProperty(t, method, *properties, "java.io.tmpdir", "/tmp");
  local::setProperty(t, method, *properties, "java.home", "/tmp");
  local::setProperty(t, method, *properties, "user.home", getenv("HOME"));
  local::setProperty(t, method, *properties, "user.dir", getenv("PWD"));

  // todo: set this to something sane:
  local::setProperty(t, method, *properties, "sun.boot.library.path",
                     getenv("LD_LIBRARY_PATH"));

#endif
  local::setProperty(t, method, *properties, "file.encoding", "ASCII");
#ifdef ARCH_x86_32
  local::setProperty(t, method, *properties, "os.arch", "x86");
#elif defined ARCH_x86_64
  local::setProperty(t, method, *properties, "os.arch", "x86_64");
#elif defined ARCH_powerpc
  local::setProperty(t, method, *properties, "os.arch", "ppc");
#elif defined ARCH_arm
  local::setProperty(t, method, *properties, "os.arch", "arm");
#else
  local::setProperty(t, method, *properties, "os.arch", "unknown");
#endif

  for (unsigned i = 0; i < t->m->propertyCount; ++i) {
    const char* start = t->m->properties[i];
    const char* p = start;
    while (*p and *p != '=') ++p;

    if (*p == '=') {
      RUNTIME_ARRAY(char, name, (p - start) + 1);
      memcpy(name, start, p - start);
      name[p - start] = 0;
      local::setProperty
        (t, method, *properties, RUNTIME_ARRAY_BODY(name), p + 1);
    }
  }

  return properties;
}

extern "C" JNIEXPORT void JNICALL
JVM_OnExit(void (*)(void)) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_Exit(jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_Halt(jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GC()
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  ENTER(t, Thread::ActiveState);

  collect(t, Heap::MajorCollection);  
}

extern "C" JNIEXPORT jlong JNICALL
JVM_MaxObjectInspectionAge(void) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_TraceInstructions(jboolean) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_TraceMethodCalls(jboolean) { abort(); }

extern "C" JNIEXPORT jlong JNICALL
JVM_TotalMemory(void) { abort(); }

extern "C" JNIEXPORT jlong JNICALL
JVM_FreeMemory(void) { abort(); }

extern "C" JNIEXPORT jlong JNICALL
JVM_MaxMemory(void) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_ActiveProcessorCount(void) { abort(); }

extern "C" JNIEXPORT void* JNICALL
JVM_LoadLibrary(const char* name)
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  ENTER(t, Thread::ActiveState);

  return loadLibrary(t, name, false, false);
}

extern "C" JNIEXPORT void JNICALL
JVM_UnloadLibrary(void*) { abort(); }

extern "C" JNIEXPORT void* JNICALL
JVM_FindLibraryEntry(void* library, const char* name)
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  ENTER(t, Thread::ActiveState);

  return static_cast<System::Library*>(library)->resolve(name);
}

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsSupportedJNIVersion(jint version)
{
  return version <= JNI_VERSION_1_4;
}

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsNaN(jdouble) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_FillInStackTrace(Thread* t, jobject throwable)
{
  ENTER(t, Thread::ActiveState);

  object trace = getTrace(t, 1);
  set(t, *throwable, ThrowableTrace, trace);
}

extern "C" JNIEXPORT void JNICALL
JVM_PrintStackTrace(Thread*, jobject, jobject) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetStackTraceDepth(Thread* t, jobject throwable)
{
  ENTER(t, Thread::ActiveState);

  return arrayLength(t, throwableTrace(t, *throwable));
}

extern "C" JNIEXPORT jobject JNICALL
JVM_GetStackTraceElement(Thread* t, jobject throwable, jint index)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, makeStackTraceElement
     (t, arrayBody(t, throwableTrace(t, *throwable), index)));
}

extern "C" JNIEXPORT void JNICALL
JVM_InitializeCompiler (Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsSilentCompiler(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_CompileClass(Thread*, jclass, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_CompileClasses(Thread*, jclass, jstring) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_CompilerCommand(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_EnableCompiler(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_DisableCompiler(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_StartThread(Thread* t, jobject thread)
{
  ENTER(t, Thread::ActiveState);

  startThread(t, *thread);
}

extern "C" JNIEXPORT void JNICALL
JVM_StopThread(Thread*, jobject, jobject) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsThreadAlive(Thread* t, jobject thread)
{
  ENTER(t, Thread::ActiveState);

  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *thread));
  if (p) {
    switch (p->state) {
    case Thread::ActiveState:
    case Thread::IdleState:
    case Thread::ExclusiveState:
      return true;

    case Thread::NoState:
    case Thread::ZombieState:
    case Thread::JoinedState:
    case Thread::ExitState:
      return false;

    default:
      abort(t);
    }
  } else {
    return false;
  }
}

extern "C" JNIEXPORT void JNICALL
JVM_SuspendThread(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_ResumeThread(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_SetThreadPriority(Thread*, jobject, jint)
{
  // ignore
}

extern "C" JNIEXPORT void JNICALL
JVM_Yield(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_Sleep(Thread* t, jclass, jlong milliseconds)
{
  ENTER(t, Thread::ActiveState);

  if (threadSleepLock(t, t->javaThread) == 0) {
    object lock = makeJobject(t);
    set(t, t->javaThread, ThreadSleepLock, lock);
  }

  acquire(t, threadSleepLock(t, t->javaThread));
  vm::wait(t, threadSleepLock(t, t->javaThread), milliseconds);
  release(t, threadSleepLock(t, t->javaThread));
}

extern "C" JNIEXPORT jobject JNICALL
JVM_CurrentThread(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->javaThread);
}

extern "C" JNIEXPORT jint JNICALL
JVM_CountStackFrames(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_Interrupt(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsInterrupted(Thread*, jobject, jboolean) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_HoldsLock(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_DumpAllStacks(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetAllThreads(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_DumpThreads(Thread*, jclass, jobjectArray) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_CurrentLoadedClass(Thread*) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_CurrentClassLoader(Thread*) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassContext(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  object trace = getTrace(t, 1);
  PROTECT(t, trace);

  object context = makeObjectArray
    (t, t->m->loader, arrayBody(t, t->m->types, Machine::JclassType),
     arrayLength(t, trace));
  PROTECT(t, context);

  for (unsigned i = 0; i < arrayLength(t, trace); ++i) {
    object c = getJClass
      (t, methodClass(t, traceElementMethod(t, arrayBody(t, trace, i))));

    set(t, context, ArrayBody + (i * BytesPerWord), c);
  }

  return makeLocalReference(t, context);
}

extern "C" JNIEXPORT jint JNICALL
JVM_ClassDepth(Thread*, jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_ClassLoaderDepth(Thread*) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
JVM_GetSystemPackage(Thread*, jstring) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetSystemPackages(Thread*) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_AllocateNewObject(Thread*, jobject, jclass,
                      jclass) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_AllocateNewArray(Thread*, jobject, jclass,
                     jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_LatestUserDefinedLoader(Thread*) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_LoadClass0(Thread*, jobject, jclass,
               jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetArrayLength(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_GetArrayElement(Thread*, jobject, jint) { abort(); }

extern "C" JNIEXPORT jvalue JNICALL
JVM_GetPrimitiveArrayElement(Thread*, jobject, jint, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_SetArrayElement(Thread*, jobject, jint, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_SetPrimitiveArrayElement(Thread*, jobject, jint, jvalue,
                             unsigned char) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_NewArray(Thread* t, jclass elementClass, jint length)
{
  ENTER(t, Thread::ActiveState);

  object c = jclassVmClass(t, *elementClass);

  if (classVmFlags(t, c) & PrimitiveFlag) {
    const char* name = reinterpret_cast<char*>
      (&byteArrayBody(t, local::getClassName(t, c), 0));

    switch (*name) {
    case 'b':
      if (name[1] == 'o') {
        return makeLocalReference(t, makeBooleanArray(t, length));
      } else {
        return makeLocalReference(t, makeByteArray(t, length));
      }
    case 'c': return makeLocalReference(t, makeCharArray(t, length));
    case 'd': return makeLocalReference(t, makeDoubleArray(t, length));
    case 'f': return makeLocalReference(t, makeFloatArray(t, length));
    case 'i': return makeLocalReference(t, makeIntArray(t, length));
    case 'l': return makeLocalReference(t, makeLongArray(t, length));
    case 's': return makeLocalReference(t, makeShortArray(t, length));
    default: abort(t);
    }
  } else {
    return makeLocalReference
      (t, makeObjectArray(t, t->m->loader, c, length));
  }
}

extern "C" JNIEXPORT jobject JNICALL
JVM_NewMultiArray(Thread*, jclass, jintArray) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_GetCallerClass(Thread* t, int target)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, getJClass(t, methodClass(t, getCaller(t, target))));
}

extern "C" JNIEXPORT jclass JNICALL
JVM_FindPrimitiveClass(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  switch (*name) {
  case 'b':
    if (name[1] == 'o') {
      return makeLocalReference
        (t, getJClass(t, arrayBody(t, t->m->types, Machine::JbooleanType)));
    } else {
      return makeLocalReference
        (t, getJClass(t, arrayBody(t, t->m->types, Machine::JbyteType)));
    }
  case 'c':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JcharType)));
  case 'd':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JdoubleType)));
  case 'f':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JfloatType)));
  case 'i':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JintType)));
  case 'l':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JlongType)));
  case 's':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JshortType)));
  case 'v':
    return makeLocalReference
      (t, getJClass(t, arrayBody(t, t->m->types, Machine::JvoidType)));
  default:
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::IllegalArgumentExceptionType);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
JVM_ResolveClass(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_FindClassFromClassLoader(Thread* t, const char* name, jboolean init,
                             jobject loader, jboolean throwError)
{
  ENTER(t, Thread::ActiveState);

  object c = resolveClass(t, loader ? *loader : t->m->loader, name);
  if (t->exception) {
    if (throwError) {
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::NoClassDefFoundErrorType,
         throwableMessage(t, t->exception),
         throwableTrace(t, t->exception),
         throwableCause(t, t->exception));
    }
    return 0;
  }

  if (init) {
    PROTECT(t, c);

    initClass(t, c);
  }

  return makeLocalReference(t, getJClass(t, c));
}

extern "C" JNIEXPORT jclass JNICALL
JVM_FindClassFromClass(Thread*, const char*, jboolean,
                       jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_FindLoadedClass(Thread* t, jobject loader, jstring name)
{
  ENTER(t, Thread::ActiveState);

  ACQUIRE(t, t->m->classLock);

  object spec = makeByteArray(t, stringLength(t, *name) + 1);
  { char* s = reinterpret_cast<char*>(&byteArrayBody(t, spec, 0));
    stringChars(t, *name, s);
    replace('.', '/', s);
  }

  object c;
  if (loader == 0 or *loader == t->m->loader) {
    c = findLoadedSystemClass(t, spec);
  } else {
    object map = classLoaderMap(t, *loader);
    if (map) {
      c = hashMapFind(t, map, spec, byteArrayHash, byteArrayEqual);
    } else {
      c = 0;
    }
  }
    
  return c ? makeLocalReference(t, getJClass(t, c)) : 0;
}

extern "C" JNIEXPORT jclass JNICALL
JVM_DefineClass(Thread*, const char*, jobject, const jbyte*,
                jsize, jobject) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_DefineClassWithSource(Thread*, const char*, jobject,
                          const jbyte*, jsize, jobject,
                          const char*) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
JVM_GetClassName(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, jclassName(t, *c));
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassInterfaces(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object table = classInterfaceTable(t, jclassVmClass(t, *c));
  if (table) {
    unsigned stride =
      (classFlags(t, jclassVmClass(t, *c)) & ACC_INTERFACE) == 0 ? 2 : 1;

    object array = makeObjectArray
       (t, t->m->loader, arrayBody(t, t->m->types, Machine::JclassType),
        arrayLength(t, table) / stride);
    PROTECT(t, array);

    for (unsigned i = 0; i < objectArrayLength(t, array); ++i) {
      object interface = getJClass(t, arrayBody(t, table, i * stride));
      set(t, array, ArrayBody + (i * BytesPerWord), interface);
    }

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray
       (t, t->m->loader, arrayBody(t, t->m->types, Machine::JclassType),
        0));
  }
}

extern "C" JNIEXPORT jobject JNICALL
JVM_GetClassLoader(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object loader = classLoader(t, jclassVmClass(t, *c));
  return loader == t->m->loader ? 0 : makeLocalReference(t, loader);
}

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsInterface(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return (classFlags(t, jclassVmClass(t, *c)) & ACC_INTERFACE) != 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassSigners(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_SetClassSigners(Thread*, jclass, jobjectArray) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_GetProtectionDomain(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_SetProtectionDomain(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsArrayClass(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return classArrayDimensions(t, jclassVmClass(t, *c)) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsPrimitiveClass(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return (classVmFlags(t, jclassVmClass(t, *c)) & PrimitiveFlag) != 0;
}

extern "C" JNIEXPORT jclass JNICALL
JVM_GetComponentType(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  uint8_t n = byteArrayBody(t, className(t, jclassVmClass(t, *c)), 1);
  if (n != 'L' and n != '[') {
    return makeLocalReference(t, getJClass(t, primitiveClass(t, n)));
  } else {
    return makeLocalReference
      (t, getJClass(t, classStaticTable(t, jclassVmClass(t, *c))));
  }
}

extern "C" JNIEXPORT jint JNICALL
JVM_GetClassModifiers(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return classFlags(t, jclassVmClass(t, *c));
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetDeclaredClasses(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_GetDeclaringClass(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
JVM_GetClassSignature(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jbyteArray JNICALL
JVM_GetClassAnnotations(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, addendumAnnotationTable(t, classAddendum(t, jclassVmClass(t, *c))));
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredMethods(Thread* t, jclass c, jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

  object table = classMethodTable(t, jclassVmClass(t, *c));
  if (table) {
    object array = makeObjectArray
      (t, t->m->loader, arrayBody(t, t->m->types, Machine::JmethodType),
       local::countMethods(t, jclassVmClass(t, *c), publicOnly));
    PROTECT(t, array);

    unsigned ai = 0;
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object vmMethod = arrayBody(t, table, i);
      PROTECT(t, vmMethod);

      if (((not publicOnly) or (methodFlags(t, vmMethod) & ACC_PUBLIC))
          and byteArrayBody(t, methodName(t, vmMethod), 0) != '<')
      {
        object name = intern
          (t, t->m->classpath->makeString
           (t, methodName(t, vmMethod), 0, byteArrayLength
            (t, methodName(t, vmMethod)) - 1));
        PROTECT(t, name);

        unsigned parameterCount;
        unsigned returnTypeSpec;
        object parameterTypes = local::resolveParameterJTypes
          (t, classLoader(t, jclassVmClass(t, *c)), methodSpec(t, vmMethod),
           &parameterCount, &returnTypeSpec);

        if (UNLIKELY(t->exception)) return 0;

        PROTECT(t, parameterTypes);

        object returnType = local::resolveJType
          (t, classLoader(t, jclassVmClass(t, *c)), reinterpret_cast<char*>
           (&byteArrayBody(t, methodSpec(t, vmMethod), returnTypeSpec)),
           byteArrayLength(t, methodSpec(t, vmMethod)) - 1 - returnTypeSpec);

        if (UNLIKELY(t->exception)) return 0;

        PROTECT(t, returnType);

        object exceptionTypes = local::resolveExceptionJTypes
          (t, classLoader(t, jclassVmClass(t, *c)),
           methodAddendum(t, vmMethod));

        if (UNLIKELY(t->exception)) return 0;

        PROTECT(t, exceptionTypes);

        object signature = t->m->classpath->makeString
          (t, methodSpec(t, vmMethod), 0, byteArrayLength
           (t, methodSpec(t, vmMethod)) - 1);

        object annotationTable = methodAddendum(t, vmMethod) == 0
          ? 0 : addendumAnnotationTable(t, methodAddendum(t, vmMethod));

        if (annotationTable) {
          fprintf(stderr, "method %s.%s has annotations\n",
                  &byteArrayBody(t, className(t, jclassVmClass(t, *c)), 0),
                  &byteArrayBody(t, methodName(t, vmMethod), 0));

          set(t, classAddendum(t, jclassVmClass(t, *c)), AddendumPool,
              addendumPool(t, methodAddendum(t, vmMethod)));
        }

        object method = makeJmethod
          (t, true, *c, i, name, returnType, parameterTypes, exceptionTypes,
           methodFlags(t, vmMethod), signature, 0, annotationTable, 0, 0, 0, 0,
           0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), method);
      }
    }

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray
       (t, t->m->loader, arrayBody(t, t->m->types, Machine::JmethodType),
        0));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredFields(Thread* t, jclass c, jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

  object table = classFieldTable(t, jclassVmClass(t, *c));
  if (table) {
    object array = makeObjectArray
      (t, t->m->loader, arrayBody(t, t->m->types, Machine::JfieldType),
       local::countFields(t, jclassVmClass(t, *c), publicOnly));
    PROTECT(t, array);

    unsigned ai = 0;
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object vmField = arrayBody(t, table, i);
      PROTECT(t, vmField);

      if ((not publicOnly) or (fieldFlags(t, vmField) & ACC_PUBLIC)) {
        object name = intern
          (t, t->m->classpath->makeString
           (t, fieldName(t, vmField), 0, byteArrayLength
            (t, fieldName(t, vmField)) - 1));
        PROTECT(t, name);

        object type = local::resolveClassBySpec
          (t, classLoader(t, jclassVmClass(t, *c)),
           reinterpret_cast<char*>
           (&byteArrayBody(t, fieldSpec(t, vmField), 0)),
           byteArrayLength(t, fieldSpec(t, vmField)) - 1);

        if (UNLIKELY(t->exception)) {
          return 0;
        }

        type = getJClass(t, type);

        object signature = t->m->classpath->makeString
          (t, fieldSpec(t, vmField), 0, byteArrayLength
           (t, fieldSpec(t, vmField)) - 1);

        object annotationTable = fieldAddendum(t, vmField) == 0
          ? 0 : addendumAnnotationTable(t, fieldAddendum(t, vmField));

        if (annotationTable) {
          set(t, classAddendum(t, jclassVmClass(t, *c)), AddendumPool,
              addendumPool(t, fieldAddendum(t, vmField)));
        }

        object field = makeJfield
          (t, true, *c, i, name, type, fieldFlags
           (t, vmField), signature, 0, annotationTable, 0, 0, 0, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), field);
      }
    }

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray
       (t, t->m->loader, arrayBody(t, t->m->types, Machine::JfieldType), 0));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetClassDeclaredConstructors(Thread* t, jclass c, jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

  object table = classMethodTable(t, jclassVmClass(t, *c));
  if (table) {
    object array = makeObjectArray
      (t, t->m->loader, arrayBody(t, t->m->types, Machine::JconstructorType),
       local::countConstructors(t, jclassVmClass(t, *c), publicOnly));
    PROTECT(t, array);

    unsigned ai = 0;
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object vmMethod = arrayBody(t, table, i);
      PROTECT(t, vmMethod);

      if (((not publicOnly) or (methodFlags(t, vmMethod) & ACC_PUBLIC))
          and strcmp(reinterpret_cast<char*>
                     (&byteArrayBody(t, methodName(t, vmMethod), 0)),
                     "<init>") == 0)
      {
        unsigned parameterCount;
        unsigned returnTypeSpec;
        object parameterTypes = local::resolveParameterJTypes
          (t, classLoader(t, jclassVmClass(t, *c)), methodSpec(t, vmMethod),
           &parameterCount, &returnTypeSpec);

        if (UNLIKELY(t->exception)) return 0;

        PROTECT(t, parameterTypes);

        object exceptionTypes = local::resolveExceptionJTypes
          (t, classLoader(t, jclassVmClass(t, *c)),
           methodAddendum(t, vmMethod));

        if (UNLIKELY(t->exception)) return 0;

        PROTECT(t, exceptionTypes);

        object signature = t->m->classpath->makeString
          (t, methodSpec(t, vmMethod), 0, byteArrayLength
           (t, methodSpec(t, vmMethod)) - 1);

        object annotationTable = methodAddendum(t, vmMethod) == 0
          ? 0 : addendumAnnotationTable(t, methodAddendum(t, vmMethod));

        if (annotationTable) {
          set(t, classAddendum(t, jclassVmClass(t, *c)), AddendumPool,
              addendumPool(t, methodAddendum(t, vmMethod)));
        }

        object method = makeJconstructor
          (t, true, *c, i, parameterTypes, exceptionTypes, methodFlags
           (t, vmMethod), signature, 0, annotationTable, 0, 0, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), method);
      }
    }

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray
       (t, t->m->loader, arrayBody(t, t->m->types, Machine::JconstructorType),
        0));
  }
}

extern "C" JNIEXPORT jint JNICALL
JVM_GetClassAccessFlags(Thread* t, jclass c)
{
  return JVM_GetClassModifiers(t, c);
}

extern "C" JNIEXPORT jobject JNICALL
JVM_InvokeMethod(Thread* t, jobject method, jobject instance,
                 jobjectArray arguments)
{
  ENTER(t, Thread::ActiveState);

  object vmMethod = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, jmethodClazz(t, *method))),
      jmethodSlot(t, *method));

  object result;
  if (arguments) {
    result = t->m->processor->invokeArray
      (t, vmMethod, instance ? *instance : 0, *arguments);
  } else {
    result = t->m->processor->invoke(t, vmMethod, instance ? *instance : 0);
  }

  return result ? makeLocalReference(t, result) : 0;
}

extern "C" JNIEXPORT jobject JNICALL
JVM_NewInstanceFromConstructor(Thread* t, jobject constructor,
                               jobjectArray arguments)
{
  ENTER(t, Thread::ActiveState);

  object instance = make
    (t, jclassVmClass(t, jconstructorClazz(t, *constructor)));
  PROTECT(t, instance);

  object method = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, jconstructorClazz(t, *constructor))),
      jconstructorSlot(t, *constructor));

  if (arguments) {
    t->m->processor->invokeArray(t, method, instance, *arguments);
  } else {
    t->m->processor->invoke(t, method, instance);
  }

  if (UNLIKELY(t->exception)) {
    return 0;
  } else {
    return makeLocalReference(t, instance);
  }
}

extern "C" JNIEXPORT jobject JNICALL
JVM_GetClassConstantPool(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, makeConstantPool
     (t, addendumPool(t, classAddendum(t, jclassVmClass(t, *c)))));
}

extern "C" JNIEXPORT jint JNICALL
JVM_ConstantPoolGetSize(Thread* t, jobject, jobject pool)
{
  if (pool == 0) return 0;

  ENTER(t, Thread::ActiveState);

  return singletonCount(t, *pool);
}

extern "C" JNIEXPORT jclass JNICALL
JVM_ConstantPoolGetClassAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
JVM_ConstantPoolGetClassAtIfLoaded(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetMethodAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetMethodAtIfLoaded(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_ConstantPoolGetFieldAtIfLoaded(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_ConstantPoolGetMemberRefInfoAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_ConstantPoolGetIntAt(Thread* t, jobject, jobject pool, jint index)
{
  ENTER(t, Thread::ActiveState);

  return singletonValue(t, *pool, index - 1);
}

extern "C" JNIEXPORT jlong JNICALL
JVM_ConstantPoolGetLongAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jfloat JNICALL
JVM_ConstantPoolGetFloatAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jdouble JNICALL
JVM_ConstantPoolGetDoubleAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
JVM_ConstantPoolGetStringAt(Thread*, jobject, jobject, jint) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
JVM_ConstantPoolGetUTF8At(Thread* t, jobject, jobject pool, jint index)
{
  ENTER(t, Thread::ActiveState);

  object array = singletonObject(t, *pool, index - 1);

  return makeLocalReference
    (t, t->m->classpath->makeString
     (t, array, 0, cast<uintptr_t>(array, BytesPerWord) - 1));
}

extern "C" JNIEXPORT jobject JNICALL
JVM_DoPrivileged
(Thread* t, jclass, jobject action, jobject, jboolean wrapException)
{
  ENTER(t, Thread::ActiveState);

  object privilegedAction = resolveClass
    (t, t->m->loader, "java/security/PrivilegedAction");
  
  if (UNLIKELY(t->exception)) {
    return 0;
  }

  object method;
  if (instanceOf(t, privilegedAction, *action)) {
    method = resolveMethod
      (t, privilegedAction, "run", "()Ljava/lang/Object;");
  } else {
    object privilegedExceptionAction = resolveClass
      (t, t->m->loader, "java/security/PrivilegedExceptionAction");
    
    if (UNLIKELY(t->exception)) {
      return 0;
    }

    method = resolveMethod
      (t, privilegedExceptionAction, "run", "()Ljava/lang/Object;");
  }

  if (LIKELY(t->exception == 0)) {
    object result = t->m->processor->invoke(t, method, *action);

    if (LIKELY(t->exception == 0)) {
      return makeLocalReference(t, result);
    } else {
      if (wrapException and not
          (instanceOf
           (t, arrayBody(t, t->m->types, Machine::ErrorType), t->exception)
           or instanceOf
           (t, arrayBody(t, t->m->types, Machine::RuntimeExceptionType),
            t->exception)))
      {
        object cause = t->exception;
        PROTECT(t, cause);

        t->exception = 0;

        object paeClass = resolveClass
          (t, t->m->loader, "java/security/PrivilegedActionException");

        if (LIKELY(t->exception == 0)) {
          PROTECT(t, paeClass);

          object paeConstructor = resolveMethod
            (t, paeClass, "<init>", "(Ljava/lang/Exception;)V");
          PROTECT(t, paeConstructor);

          if (LIKELY(t->exception == 0)) {
            object result = make(t, paeClass);
            PROTECT(t, result);
    
            t->m->processor->invoke(t, paeConstructor, result, cause);

            if (LIKELY(t->exception == 0)) {
              t->exception = result;
            }
          }
        }
      }
    }
  }

  return 0;
}

extern "C" JNIEXPORT jobject JNICALL
JVM_GetInheritedAccessControlContext(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_GetStackAccessControlContext(Thread*, jclass)
{
  return 0;
}

extern "C" JNIEXPORT void* JNICALL
JVM_RegisterSignal(jint, void*) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_RaiseSignal(jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_FindSignal(const char*)
{
  return -1;
}

extern "C" JNIEXPORT jboolean JNICALL
JVM_DesiredAssertionStatus(Thread*, jclass, jclass)
{
  return false;
}

extern "C" JNIEXPORT jobject JNICALL
JVM_AssertionStatusDirectives(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_SupportsCX8()
{
  return true;
}

extern "C" JNIEXPORT const char* JNICALL
JVM_GetClassNameUTF(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GetClassCPTypes(Thread*, jclass, unsigned char*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetClassCPEntriesCount(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetClassFieldsCount(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetClassMethodsCount(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionIndexes(Thread*, jclass, jint,
                                unsigned short*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionsCount(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GetMethodIxByteCode(Thread*, jclass, jint,
                        unsigned char*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxByteCodeLength(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GetMethodIxExceptionTableEntry(Thread*, jclass, jint,
                                   jint,
                                   local::JVM_ExceptionTableEntryType*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxExceptionTableLength(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetFieldIxModifiers(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxModifiers(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxLocalsCount(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxArgsSize(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetMethodIxMaxStack(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsConstructorIx(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetMethodIxNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetMethodIxSignatureUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPFieldNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPMethodNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPMethodSignatureUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPFieldSignatureUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPClassNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPFieldClassNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
JVM_GetCPMethodClassNameUTF(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetCPFieldModifiers(Thread*, jclass, int, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetCPMethodModifiers(Thread*, jclass, int, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_ReleaseUTF(const char*) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
JVM_IsSameClassPackage(Thread*, jclass, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetLastErrorString(char* dst, int length)
{
  strncpy(dst, strerror(errno), length);
  return strlen(dst);
}

extern "C" JNIEXPORT char* JNICALL
JVM_NativePath(char* path)
{
  return path;
}

extern "C" JNIEXPORT jint JNICALL
JVM_Open(const char* name, jint flags, jint mode)
{
  return OPEN(name, flags, mode);
}

extern "C" JNIEXPORT jint JNICALL
JVM_Close(jint fd)
{
  return CLOSE(fd);
}

extern "C" JNIEXPORT jint JNICALL
JVM_Read(jint fd, char* dst, jint length)
{
  return READ(fd, dst, length);
}

extern "C" JNIEXPORT jint JNICALL
JVM_Write(jint fd, char* src, jint length)
{
  return WRITE(fd, src, length);
}

extern "C" JNIEXPORT jint JNICALL
JVM_Available(jint fd, jlong* result)
{
  int current = LSEEK(fd, 0, SEEK_CUR);
  if (current == -1) return 0;

  int end = LSEEK(fd, 0, SEEK_END);
  if (end == -1) return 0;

  if (LSEEK(fd, current, SEEK_SET) == -1) return 0;

  *result = end - current;
  return 1;
}

extern "C" JNIEXPORT jlong JNICALL
JVM_Lseek(jint fd, jlong offset, jint start)
{
  return LSEEK(fd, offset, start);
}

extern "C" JNIEXPORT jint JNICALL
JVM_SetLength(jint, jlong) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Sync(jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_InitializeSocketLibrary(void) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Socket(jint, jint, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_SocketClose(jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_SocketShutdown(jint, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Recv(jint, char*, jint, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Send(jint, char*, jint, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Timeout(int, long) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Listen(jint, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Connect(jint, struct sockaddr*, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Bind(jint, struct sockaddr*, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_Accept(jint, struct sockaddr*, jint*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_RecvFrom(jint, char*, int,
             int, struct sockaddr*, int*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_SendTo(jint, char*, int,
           int, struct sockaddr*, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_SocketAvailable(jint, jint*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetSockName(jint, struct sockaddr*, int*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_GetSockOpt(jint, int, int, char*, int*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
JVM_SetSockOpt(jint, int, int, const char*, int) { abort(); }

extern "C" JNIEXPORT struct protoent* JNICALL
JVM_GetProtoByName(char*) { abort(); }

extern "C" JNIEXPORT struct hostent* JNICALL
JVM_GetHostByAddr(const char*, int, int) { abort(); }

extern "C" JNIEXPORT struct hostent* JNICALL
JVM_GetHostByName(char*) { abort(); }

extern "C" JNIEXPORT int JNICALL
JVM_GetHostName(char*, int) { abort(); }

extern "C" JNIEXPORT void* JNICALL
JVM_RawMonitorCreate(void)
{
  System* s = local::globalMachine->system;
  System::Monitor* lock;
  if (s->success(s->make(&lock))) {
    return lock;
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
JVM_RawMonitorDestroy(void* lock)
{
  static_cast<System::Monitor*>(lock)->dispose();
}

extern "C" JNIEXPORT jint JNICALL
JVM_RawMonitorEnter(void* lock)
{
  static_cast<System::Monitor*>(lock)->acquire
    (static_cast<Thread*>
     (local::globalMachine->localThread->get())->systemThread);

  return 0;
}

extern "C" JNIEXPORT void JNICALL
JVM_RawMonitorExit(void* lock)
{
  static_cast<System::Monitor*>(lock)->release
    (static_cast<Thread*>
     (local::globalMachine->localThread->get())->systemThread);
}

extern "C" JNIEXPORT void* JNICALL
JVM_GetManagement(jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
JVM_InitAgentProperties(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetEnclosingMethodInfo(JNIEnv*, jclass) { abort(); }

extern "C" JNIEXPORT jintArray JNICALL
JVM_GetThreadStateValues(JNIEnv*, jint) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
JVM_GetThreadStateNames(JNIEnv*, jint, jintArray) { abort(); }

extern "C" JNIEXPORT void JNICALL
JVM_GetVersionInfo(JNIEnv*, local::jvm_version_info*, size_t) { abort(); }

extern "C" JNIEXPORT int
jio_vsnprintf(char* dst, size_t size, const char* format, va_list a)
{
  return vm::vsnprintf(dst, size, format, a);
}

extern "C" JNIEXPORT int
jio_snprintf(char* dst, size_t size, const char* format, ...)
{
  va_list a;
  va_start(a, format);

  int r = jio_vsnprintf(dst, size, format, a);

  va_end(a);

  return r;
}

extern "C" JNIEXPORT int
jio_vfprintf(FILE* stream, const char* format, va_list a)
{
  return vfprintf(stream, format, a);
}

extern "C" JNIEXPORT int
jio_fprintf(FILE* stream, const char* format, ...)
{
  va_list a;
  va_start(a, format);

  int r = jio_vfprintf(stream, format, a);

  va_end(a);

  return r;
}
