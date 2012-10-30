/* Copyright (c) 2010-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"
#include "classpath-common.h"
#include "util.h"
#include "process.h"

#ifdef PLATFORM_WINDOWS

#  include <windows.h>
#  include <io.h>
#  include <direct.h>
#  include <share.h>
#  include <errno.h>
#  include <fcntl.h>
#  include <sys/stat.h>
#  include <sys/types.h>

#  undef interface

#  define CLOSE _close
#  define READ _read
#  define WRITE _write
#  define FSTAT _fstat
#  define STAT _stat
#  define LSEEK _lseek

#  define S_ISSOCK(x) false

#  ifdef _MSC_VER
#    define S_ISREG(x) ((x) | _S_IFREG)
#    define S_ISDIR(x) ((x) | _S_IFDIR)
#    define S_IRUSR _S_IREAD
#    define S_IWUSR _S_IWRITE
#  else
#    define OPEN _open
#  endif

#  define O_RDONLY _O_RDONLY

#  ifdef AVIAN_OPENJDK_SRC
#    define EXPORT(x) x
#  else
#    define EXPORT(x) _##x
#  endif

typedef int socklen_t;

#  define RTLD_DEFAULT 0

#else // not PLATFORM_WINDOWS

#  include <unistd.h>
#  include <limits.h>
#  include <sys/types.h>
#  include <sys/stat.h>
#  include <sys/socket.h>
#  include <sys/ioctl.h>
#  include <fcntl.h>
#  include <errno.h>
#  include <sched.h>
#  include <dlfcn.h>

#  define OPEN open
#  define CLOSE close
#  define READ read
#  define WRITE write
#  define STAT stat
#  define FSTAT fstat
#  define LSEEK lseek

#  define EXPORT(x) x

#endif // not PLATFORM_WINDOWS

#define JVM_EEXIST -100

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
#endif

namespace local {

const int JMM_VERSION_1_0 = 0x20010000;

struct jmmOptionalSupport {
  unsigned isLowMemoryDetectionSupported : 1;
  unsigned isCompilationTimeMonitoringSupported : 1;
  unsigned isThreadContentionMonitoringSupported : 1;
  unsigned isCurrentThreadCpuTimeSupported : 1;
  unsigned isOtherThreadCpuTimeSupported : 1;
  unsigned isBootClassPathSupported : 1;
  unsigned isObjectMonitorUsageSupported : 1;
  unsigned isSynchronizerUsageSupported : 1;
};

typedef unsigned jmmLongAttribute;
typedef unsigned jmmBoolAttribute;
typedef unsigned jmmStatisticType;
typedef unsigned jmmThresholdType;
typedef unsigned jmmVMGlobalType;
typedef unsigned jmmVMGlobalOrigin;

struct jmmVMGlobal {
  jstring name;
  jvalue value;
  jmmVMGlobalType type;
  jmmVMGlobalOrigin origin;
  unsigned writeable : 1;
  unsigned external : 1;
  unsigned reserved : 30;
  void* reserved1;
  void* reserved2;
};

struct jmmExtAttributeInfo {
  const char* name;
  char type;
  const char* description;
};

struct jmmGCStat {
  jlong gc_index;
  jlong start_time;
  jlong end_time;
  jobjectArray usage_before_gc;
  jobjectArray usage_after_gc;
  jint gc_ext_attribute_values_size;
  jvalue* gc_ext_attribute_values;
  jint num_gc_ext_attributes;
};

struct JmmInterface {
  void* reserved1;
  void* reserved2;

  jint
  (JNICALL *GetVersion)
    (JNIEnv*);

  jint
  (JNICALL *GetOptionalSupport)
    (JNIEnv*, jmmOptionalSupport*);

  jobject
  (JNICALL *GetInputArguments)
    (JNIEnv*);

  jint
  (JNICALL *GetThreadInfo)
    (JNIEnv*, jlongArray, jint, jobjectArray);

  jobjectArray
  (JNICALL *GetInputArgumentArray)
    (JNIEnv*);

  jobjectArray
  (JNICALL *GetMemoryPools)
    (JNIEnv*, jobject);

  jobjectArray
  (JNICALL *GetMemoryManagers)
    (JNIEnv*, jobject);

  jobject
  (JNICALL *GetMemoryPoolUsage)
    (JNIEnv*, jobject);

  jobject
  (JNICALL *GetPeakMemoryPoolUsage)
    (JNIEnv*, jobject);

  void* reserved4;

  jobject
  (JNICALL *GetMemoryUsage)
    (JNIEnv*, jboolean);

  jlong
  (JNICALL *GetLongAttribute)
    (JNIEnv*, jobject, jmmLongAttribute);

  jboolean (JNICALL *GetBoolAttribute)
    (JNIEnv*, jmmBoolAttribute);

  jboolean
  (JNICALL *SetBoolAttribute)
    (JNIEnv*, jmmBoolAttribute, jboolean);

  jint
  (JNICALL *GetLongAttributes)
    (JNIEnv*, jobject, jmmLongAttribute*, jint, jlong*);

  jobjectArray
  (JNICALL *FindCircularBlockedThreads)
    (JNIEnv*);

  jlong
  (JNICALL *GetThreadCpuTime)
  (JNIEnv*, jlong);

  jobjectArray
  (JNICALL *GetVMGlobalNames)
    (JNIEnv*);

  jint
  (JNICALL *GetVMGlobals)
    (JNIEnv*, jobjectArray, jmmVMGlobal*, jint);

  jint
  (JNICALL *GetInternalThreadTimes)
    (JNIEnv*, jobjectArray, jlongArray);

  jboolean
  (JNICALL *ResetStatistic)
    (JNIEnv*, jvalue, jmmStatisticType);

  void
  (JNICALL *SetPoolSensor)
  (JNIEnv*, jobject, jmmThresholdType, jobject);

  jlong
  (JNICALL *SetPoolThreshold)
    (JNIEnv*, jobject, jmmThresholdType, jlong);

  jobject
  (JNICALL *GetPoolCollectionUsage)
  (JNIEnv*, jobject);

  jint
  (JNICALL *GetGCExtAttributeInfo)
    (JNIEnv*, jobject, jmmExtAttributeInfo*, jint);

  void
  (JNICALL *GetLastGCStat)
  (JNIEnv*, jobject, jmmGCStat*);

  jlong
  (JNICALL *GetThreadCpuTimeWithKind)
    (JNIEnv*, jlong, jboolean);

  void* reserved5;

  jint
  (JNICALL *DumpHeap0)
    (JNIEnv*, jstring, jboolean);

  jobjectArray
  (JNICALL *FindDeadlocks)
    (JNIEnv*, jboolean);

  void
  (JNICALL *SetVMGlobal)
  (JNIEnv*, jstring, jvalue );

  void* reserved6;

  jobjectArray
  (JNICALL *DumpThreads)
    (JNIEnv*, jlongArray, jboolean, jboolean);
};

const unsigned InterfaceVersion = 4;
const unsigned PageSize = 4 * 1024;
const int VirtualFileBase = 1000000000;

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
  THREAD_RUNTIME_ARRAY(t, char, s, byteArrayLength(t, name));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, name, 0)));

  return makeString(t, "%s", s);
}

void
interceptFileOperations(Thread*);

void
clearInterrupted(Thread*);

class MyClasspath : public Classpath {
 public:
  MyClasspath(System* s, Allocator* allocator, const char* javaHome,
              const char* embedPrefix):
    allocator(allocator), ranNetOnLoad(0), ranManagementOnLoad(0)
  {
    class StringBuilder {
     public:
      StringBuilder(System* s, Allocator* allocator):
        s(s),
        allocator(allocator),
        bufferSize(1024),
        buffer(static_cast<char*>(allocator->allocate(bufferSize))),
        offset(0)
      { }

      void ensure(unsigned capacity) {
        if (capacity > bufferSize) {
          unsigned size = max(bufferSize * 2, capacity);
          char* b = static_cast<char*>(allocator->allocate(size));

          if (offset) {
            memcpy(b, buffer, offset);
          }

          allocator->free(buffer, bufferSize);
          
          buffer = b;
          bufferSize = size;
        }
      }

      void append(const char* append) {
        unsigned length = strlen(append);
        ensure(offset + length + 1);
  
        strncpy(buffer + offset, append, length + 1);
        
        offset += length;
      }

      void append(char c) {
        ensure(2);
        
        buffer[offset] = c;
        buffer[offset + 1] = 0;

        ++ offset;
      }

      System* s;
      Allocator* allocator;
      unsigned bufferSize;
      char* buffer;
      unsigned offset;
    } sb(s, allocator);

    unsigned javaHomeOffset = sb.offset;
    sb.append(javaHome);
    sb.append('\0');

    unsigned classpathOffset = sb.offset;
    sb.append(AVIAN_CLASSPATH);
    sb.append(s->pathSeparator());
    sb.append(javaHome);
    sb.append("/lib/rt.jar");
    sb.append(s->pathSeparator());
    sb.append(javaHome);
    sb.append("/lib/jsse.jar");
    sb.append(s->pathSeparator());
    sb.append(javaHome);
    sb.append("/lib/jce.jar");
    sb.append(s->pathSeparator());
    sb.append(javaHome);
    sb.append("/lib/ext/sunjce_provider.jar");
    sb.append(s->pathSeparator());
    sb.append(javaHome);
    sb.append("/lib/resources.jar");
    sb.append('\0');

    unsigned libraryPathOffset = sb.offset;
    sb.append(javaHome);
#ifdef PLATFORM_WINDOWS
    sb.append("/bin");
#elif defined __APPLE__
    sb.append("/lib");
#elif defined ARCH_x86_64
    sb.append("/lib/amd64");
#elif defined ARCH_arm
    sb.append("/lib/arm");
#else
    // todo: handle other architectures
    sb.append("/lib/i386");
#endif
    sb.append('\0');

    unsigned tzMappingsOffset = sb.offset;
    sb.append(javaHome);
    sb.append("/lib/tzmappings");
    this->tzMappingsLength = sb.offset - tzMappingsOffset;
    sb.append('\0');

    unsigned embedPrefixOffset = sb.offset;
    sb.append(embedPrefix);
    this->embedPrefixLength = sb.offset - embedPrefixOffset;

    this->javaHome = sb.buffer + javaHomeOffset;
    this->classpath = sb.buffer + classpathOffset;
    this->libraryPath = sb.buffer + libraryPathOffset;
    this->tzMappings = sb.buffer + tzMappingsOffset;
    this->embedPrefix = sb.buffer + embedPrefixOffset;
    this->buffer = sb.buffer;
    this->bufferSize = sb.bufferSize;
  }

  virtual object
  makeJclass(Thread* t, object class_)
  {
    PROTECT(t, class_);

    object name = makeClassNameString(t, getClassName(t, class_));
    PROTECT(t, name);

    object c = allocate(t, FixedSizeOfJclass, true);
    setObjectClass(t, c, type(t, Machine::JclassType));
    set(t, c, JclassName, name);
    set(t, c, JclassVmClass, class_);

    return c;
  }

  virtual object
  makeString(Thread* t, object array, int32_t offset, int32_t length)
  {
    if (objectClass(t, array) == type(t, Machine::ByteArrayType)) {
      PROTECT(t, array);
      
      object charArray = makeCharArray(t, length);
      for (int i = 0; i < length; ++i) {
        charArrayBody(t, charArray, i) = byteArrayBody(t, array, offset + i);
      }

      array = charArray;
    } else {
      expect(t, objectClass(t, array) == type(t, Machine::CharArrayType));
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
      group = allocate(t, FixedSizeOfThreadGroup, true);
      setObjectClass(t, group, type(t, Machine::ThreadGroupType));
      threadGroupMaxPriority(t, group) = MaxPriority;
    }

    PROTECT(t, group);
    
    object thread = allocate(t, FixedSizeOfThread, true);
    setObjectClass(t, thread, type(t, Machine::ThreadType));
    threadPriority(t, thread) = NormalPriority;
    threadGroup(t, thread) = group;
    threadContextClassLoader(t, thread) = root(t, Machine::AppLoader);

    PROTECT(t, thread);

    object blockerLock = makeJobject(t);
    set(t, thread, ThreadBlockerLock, blockerLock);

    const unsigned BufferSize = 256;
    char buffer[BufferSize];
    unsigned length = vm::snprintf(buffer, BufferSize, "Thread-%p", thread);
    object name = makeCharArray(t, length);
    for (unsigned i = 0; i < length; ++i) {
      charArrayBody(t, name, i) = buffer[i];
    }
    set(t, thread, ThreadName, name);

    return thread;
  }

  virtual void
  clearInterrupted(Thread* t)
  {
    local::clearInterrupted(t);
  }

  virtual void
  runThread(Thread* t)
  {
    object method = resolveMethod
      (t, root(t, Machine::BootLoader), "java/lang/Thread", "run", "()V");

    t->m->processor->invoke(t, method, t->javaThread);

    acquire(t, t->javaThread);
    t->flags &= ~Thread::ActiveFlag;
    notifyAll(t, t->javaThread);
    release(t, t->javaThread);
  }

  virtual void
  resolveNative(Thread* t, object method)
  {
    if (strcmp(reinterpret_cast<const int8_t*>("sun/font/SunFontManager"),
               &byteArrayBody(t, className(t, methodClass(t, method)), 0)) == 0
        and strcmp(reinterpret_cast<const int8_t*>("initIDs"),
                   &byteArrayBody(t, methodName(t, method), 0)) == 0
        and strcmp(reinterpret_cast<const int8_t*>("()V"),
                   &byteArrayBody(t, methodSpec(t, method), 0)) == 0)
    {
      PROTECT(t, method);

      expect(t, loadLibrary(t, libraryPath, "fontmanager", true, true));
    }

    vm::resolveNative(t, method);
  }

  virtual void
  boot(Thread* t)
  {
    globalMachine = t->m;

    resolveSystemClass(t, root(t, Machine::BootLoader),
                       className(t, type(t, Machine::ClassLoaderType)));

#ifdef AVIAN_OPENJDK_SRC
    interceptFileOperations(t);
#else // not AVIAN_OPENJDK_SRC
    expect(t, loadLibrary(t, libraryPath, "verify", true, true));
    expect(t, loadLibrary(t, libraryPath, "java", true, true));
#endif // not AVIAN_OPENJDK_SRC

    { object assertionLock = resolveField
        (t, type(t, Machine::ClassLoaderType), "assertionLock",
         "Ljava/lang/Object;");

      set(t, root(t, Machine::BootLoader), fieldOffset(t, assertionLock),
          root(t, Machine::BootLoader));
    }

    { object class_ = resolveClass
        (t, root(t, Machine::BootLoader), "java/util/Properties", true,
         Machine::NoClassDefFoundErrorType);

      PROTECT(t, class_);
      
      object instance = makeNew(t, class_);

      PROTECT(t, instance);

      object constructor = resolveMethod(t, class_, "<init>", "()V");

      t->m->processor->invoke(t, constructor, instance);

      t->m->processor->invoke
        (t, root(t, Machine::BootLoader), "java/lang/System",
         "setProperties", "(Ljava/util/Properties;)V", 0, instance);
    }

    { object constructor = resolveMethod
      (t, type(t, Machine::ClassLoaderType), "<init>",
       "(Ljava/lang/ClassLoader;)V");

      PROTECT(t, constructor);

      t->m->processor->invoke(t, constructor, root(t, Machine::BootLoader), 0);

      t->m->processor->invoke
        (t, constructor, root(t, Machine::AppLoader),
         root(t, Machine::BootLoader));
    }

    { object scl = resolveField
        (t, type(t, Machine::ClassLoaderType), "scl",
         "Ljava/lang/ClassLoader;");

      PROTECT(t, scl);

      object sclSet = resolveField
        (t, type(t, Machine::ClassLoaderType), "sclSet", "Z");

      set(t, classStaticTable(t, type(t, Machine::ClassLoaderType)),
          fieldOffset(t, scl), root(t, Machine::AppLoader));

      cast<uint8_t>(classStaticTable(t, type(t, Machine::ClassLoaderType)),
                    fieldOffset(t, sclSet)) = true;
    }

    t->m->processor->invoke
      (t, root(t, Machine::BootLoader), "java/lang/System",
       "initializeSystemClass", "()V", 0);

    t->m->processor->invoke
      (t, root(t, Machine::BootLoader), "sun/misc/Launcher",
       "getLauncher", "()Lsun/misc/Launcher;", 0);

    set(t, t->javaThread, ThreadContextClassLoader,
        root(t, Machine::AppLoader));
  }

  virtual const char*
  bootClasspath()
  {
    return classpath;
  }

  virtual void
  updatePackageMap(Thread* t, object class_)
  {
    PROTECT(t, class_);

    if (root(t, Machine::PackageMap) == 0) {
      setRoot(t, Machine::PackageMap, makeHashMap(t, 0, 0));
    }

    object className = vm::className(t, class_);
    if ('[' != byteArrayBody(t, className, 0)) {
      THREAD_RUNTIME_ARRAY
        (t, char, packageName, byteArrayLength(t, className));

      char* s = reinterpret_cast<char*>(&byteArrayBody(t, className, 0));
      char* p = strrchr(s, '/');

      if (p) {
        int length = (p - s) + 1;
        memcpy(RUNTIME_ARRAY_BODY(packageName),
               &byteArrayBody(t, className, 0),
               length);
        RUNTIME_ARRAY_BODY(packageName)[length] = 0;

        object key = vm::makeByteArray(t, "%s", packageName);

        hashMapRemove
          (t, root(t, Machine::PackageMap), key, byteArrayHash,
           byteArrayEqual);

        object source = classSource(t, class_);
        if (source == 0) {
          source = vm::makeByteArray(t, "avian-dummy-package-source");
        }

        hashMapInsert
          (t, root(t, Machine::PackageMap), key, source, byteArrayHash);
      }
    }
  }

  virtual void
  dispose()
  { 
    allocator->free(buffer, bufferSize);
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
  const char* javaHome;
  const char* classpath;
  const char* libraryPath;
  const char* tzMappings;
  const char* embedPrefix;
  char* buffer;
  unsigned bufferSize;
  unsigned tzMappingsLength;
  unsigned embedPrefixLength;
  unsigned filePathField;
  unsigned fileDescriptorFdField;
  unsigned fileInputStreamFdField;
  unsigned zipFileJzfileField;
  unsigned zipEntryNameField;
  unsigned zipEntryTimeField;
  unsigned zipEntryCrcField;
  unsigned zipEntrySizeField;
  unsigned zipEntryCsizeField;
  unsigned zipEntryMethodField;
  bool ranNetOnLoad;
  bool ranManagementOnLoad;
  JmmInterface jmmInterface;
};

struct JVM_ExceptionTableEntryType {
  jint start_pc;
  jint end_pc;
  jint handler_pc;
  jint catchType;
};

struct jvm_version_info {
  unsigned jvm_version;
  unsigned update_version: 8;
  unsigned special_update_version: 8;
  unsigned reserved1: 16;
  unsigned reserved2;
  unsigned is_attach_supported: 1;
  unsigned is_kernel_jvm: 1;
  unsigned: 30;
  unsigned: 32;
  unsigned: 32;
};

Finder*
getFinder(Thread* t, const char* name, unsigned nameLength)
{
  ACQUIRE(t, t->m->referenceLock);
    
  for (object p = root(t, Machine::VirtualFileFinders);
       p; p = finderNext(t, p))
  {
    if (byteArrayLength(t, finderName(t, p)) == nameLength
        and strncmp(reinterpret_cast<const char*>
                    (&byteArrayBody(t, finderName(t, p), 0)),
                    name, nameLength))
    {
      return static_cast<Finder*>(finderFinder(t, p));
    }
  }

  object n = makeByteArray(t, nameLength + 1);
  memcpy(&byteArrayBody(t, n, 0), name, nameLength);

  void* p = t->m->libraries->resolve
    (reinterpret_cast<const char*>(&byteArrayBody(t, n, 0)));

  if (p) {
    uint8_t* (*function)(unsigned*);
    memcpy(&function, &p, BytesPerWord);

    unsigned size;
    uint8_t* data = function(&size);
    if (data) {
      Finder* f = makeFinder(t->m->system, t->m->heap, data, size);
      object finder = makeFinder
        (t, f, n, root(t, Machine::VirtualFileFinders));

      setRoot(t, Machine::VirtualFileFinders, finder);

      return f;
    }
  }

  return 0;
}

bool
pathEqual(const char* a, const char* b, unsigned length)
{
#ifdef PLATFORM_WINDOWS
  return strncasecmp(a, b, length) == 0;
#else
  return strncmp(a, b, length) == 0;
#endif
}

bool
pathEqual(const char* a, const char* b)
{
#ifdef PLATFORM_WINDOWS
  return strcasecmp(a, b) == 0;
#else
  return strcmp(a, b) == 0;
#endif
}

class EmbeddedFile {
 public:
  EmbeddedFile(MyClasspath* cp, const char* path, unsigned pathLength) {
    if (pathEqual(cp->embedPrefix, path, cp->embedPrefixLength)) {
      const char* p = path + cp->embedPrefixLength;
      while (*p == '/') ++ p;

      this->jar = p;

      if (*p == 0) {
        this->jarLength = 0;
        this->path = 0;
        this->pathLength = 0;
        return;
      }

      while (*p and *p != '/') ++p;
    
      this->jarLength = p - this->jar;

      while (*p == '/') ++p;

      this->path = p;
      this->pathLength = pathLength - (p - path);
    } else {
      this->jar = 0;
      this->jarLength =0;
      this->path = 0;
      this->pathLength = 0;
    }
  }

  const char* jar;
  const char* path;
  unsigned jarLength;
  unsigned pathLength;
};

int64_t JNICALL
getFileAttributes
(Thread* t, object method, uintptr_t* arguments)
{
  const unsigned Exists = 1;
  const unsigned Regular = 2;
  const unsigned Directory = 4;

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  object file = reinterpret_cast<object>(arguments[1]);
  object path = cast<object>(file, cp->filePathField);

  THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));
  if (ef.jar) {
    if (ef.jarLength == 0) {
      return Exists | Directory;
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder) {
      if (ef.pathLength == 0) {
        return Exists | Directory;
      }

      unsigned length;
      System::FileType type = finder->stat(ef.path, &length, true);
      switch (type) {
      case System::TypeUnknown: return Exists;
      case System::TypeDoesNotExist: return 0;
      case System::TypeFile: return Exists | Regular;
      case System::TypeDirectory: return Exists | Directory;
      default: abort(t);
      }
    } else {
      return 0;
    }
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        reinterpret_cast<object>(arguments[0]), file));
  }
}

int64_t JNICALL
checkFileAccess
(Thread* t, object method, uintptr_t* arguments)
{
  const unsigned Read = 4;
  
  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  object file = reinterpret_cast<object>(arguments[1]);
  unsigned mask = arguments[2];
  object path = cast<object>(file, cp->filePathField);

  THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));
  if (ef.jar) {
    if (ef.jarLength == 0) {
      return mask == Read;
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder) {
      if (ef.pathLength == 0) {
        return mask == Read;
      }

      unsigned length;
      System::FileType type = finder->stat(ef.path, &length, true);
      switch (type) {
      case System::TypeDoesNotExist: return false;
      case System::TypeUnknown:
      case System::TypeFile:
      case System::TypeDirectory: return mask == Read;
      default: abort(t);
      }
    } else {
      return 0;
    }
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        reinterpret_cast<object>(arguments[0]), file, mask)) != 0;
  }
}

int64_t JNICALL
getFileLength
(Thread* t, object method, uintptr_t* arguments)
{
  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  object file = reinterpret_cast<object>(arguments[1]);
  object path = cast<object>(file, cp->filePathField);

  THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));    
  if (ef.jar) {
    if (ef.jarLength == 0) {
      return 0;
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder) {
      if (ef.pathLength == 0) {
        return 0;
      }

      unsigned fileLength;
      finder->stat(ef.path, &fileLength);
      return fileLength;
    }

    return 0;
  } else {
    return longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        reinterpret_cast<object>(arguments[0]), file));
  }
}

void JNICALL
openFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object path = reinterpret_cast<object>(arguments[1]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));
  if (ef.jar) {
    if (ef.jarLength == 0 or ef.pathLength == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    System::Region* r = finder->find(ef.path);
    if (r == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    PROTECT(t, this_);

    ACQUIRE(t, t->m->referenceLock);

    int index = -1;
    unsigned oldLength = root(t, Machine::VirtualFiles)
      ? arrayLength(t, root(t, Machine::VirtualFiles)) : 0;

    for (unsigned i = 0; i < oldLength; ++i) {
      if (arrayBody(t, root(t, Machine::VirtualFiles), i) == 0) {
        index = i;
        break;
      }
    }

    if (index == -1) {
      object newArray = growArray(t, root(t, Machine::VirtualFiles));
      setRoot(t, Machine::VirtualFiles, newArray);
      index = oldLength;
    }

    object region = makeRegion(t, r, 0);
    set(t, root(t, Machine::VirtualFiles), ArrayBody + (index * BytesPerWord),
        region);

    cast<int32_t>
      (cast<object>
       (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField)
      = index + VirtualFileBase;
  } else {
    t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_, path);
  }
}

int64_t JNICALL
readByteFromFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int fd = cast<int32_t>
    (cast<object>
     (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField);

  if (fd >= VirtualFileBase) {
    ACQUIRE(t, t->m->referenceLock);
    
    object region = arrayBody
      (t, root(t, Machine::VirtualFiles), fd - VirtualFileBase);

    if (region) {
      System::Region* r = static_cast<System::Region*>
        (regionRegion(t, region));

      if (r->length() > regionPosition(t, region)) {
        return r->start()[regionPosition(t, region)++];
      } else {
        return -1;
      }
    } else {
      throwNew(t, Machine::IoExceptionType);
    }
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        this_));
  }
}

int64_t JNICALL
readBytesFromFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object dst = reinterpret_cast<object>(arguments[1]);
  int32_t offset = arguments[2];
  int32_t length = arguments[3];

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int fd = cast<int32_t>
    (cast<object>
     (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField);

  if (fd >= VirtualFileBase) {
    PROTECT(t, dst);

    ACQUIRE(t, t->m->referenceLock);
    
    object region = arrayBody
      (t, root(t, Machine::VirtualFiles), fd - VirtualFileBase);

    if (region) {
      System::Region* r = static_cast<System::Region*>
        (regionRegion(t, region));

      int available = r->length() - regionPosition(t, region);
      if (available == 0) {
        return -1;
      }

      if (length > available) {
        length = available;
      }

      memcpy(&byteArrayBody(t, dst, offset),
             r->start() + regionPosition(t, region),
             length);

      regionPosition(t, region) += length;

      return length;
    } else {
      throwNew(t, Machine::IoExceptionType);
    }
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        this_, dst, offset, length));
  }
}

int64_t JNICALL
skipBytesInFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  int64_t count; memcpy(&count, arguments + 1, 8);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int fd = cast<int32_t>
    (cast<object>
     (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField);

  if (fd >= VirtualFileBase) {
    ACQUIRE(t, t->m->referenceLock);
    
    object region = arrayBody
      (t, root(t, Machine::VirtualFiles), fd - VirtualFileBase);

    if (region) {
      System::Region* r = static_cast<System::Region*>
        (regionRegion(t, region));

      int available = r->length() - regionPosition(t, region);
      if (count > available) {
        count = available;
      }

      regionPosition(t, region) += count;

      return count;
    } else {
      throwNew(t, Machine::IoExceptionType);
    }
  } else {
    return longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        this_, count));
  }
}

int64_t JNICALL
availableBytesInFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int fd = cast<int32_t>
    (cast<object>
     (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField);

  if (fd >= VirtualFileBase) {
    ACQUIRE(t, t->m->referenceLock);
    
    object region = arrayBody
      (t, root(t, Machine::VirtualFiles), fd - VirtualFileBase);

    if (region) {
      return static_cast<System::Region*>(regionRegion(t, region))->length()
        - regionPosition(t, region);
    } else {
      throwNew(t, Machine::IoExceptionType);
    }
  } else {
    object r = t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_);

    return r ? intValue(t, r) : 0;
  }
}

void JNICALL
closeFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int fd = cast<int32_t>
    (cast<object>
     (this_, cp->fileInputStreamFdField), cp->fileDescriptorFdField);

  if (fd >= VirtualFileBase) {
    ACQUIRE(t, t->m->referenceLock);

    int index = fd - VirtualFileBase;
    object region = arrayBody(t, root(t, Machine::VirtualFiles), index);

    if (region) {
      static_cast<System::Region*>(regionRegion(t, region))->dispose();
    }

    set(t, root(t, Machine::VirtualFiles), ArrayBody + (index * BytesPerWord),
        0);
  } else {
    t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_);
  }
}

class ZipFile {
 public:
  class Entry {
   public:
    Entry(unsigned hash, const uint8_t* start, Entry* next):
      hash(hash), start(start), next(next), entry(0)
    { }

    Entry(int64_t entry):
      hash(0), start(0), next(0), entry(entry)
    { }
    
    Entry():
      hash(0), start(0), next(0), entry(0)
    { }

    unsigned hash;
    const uint8_t* start;
    Entry* next;
    int64_t entry;
  };

  ZipFile(Thread* t, System::Region* region, unsigned entryCount):
    region(region),
    entryCount(entryCount),
    indexSize(nextPowerOfTwo(entryCount)),
    index(reinterpret_cast<ZipFile::Entry**>
          (t->m->heap->allocate(sizeof(ZipFile::Entry*) * indexSize))),
    file(0)
  {
    memset(index, 0, sizeof(ZipFile::Entry*) * indexSize);
  }

  ZipFile(int64_t file):
    region(0), entryCount(0), indexSize(0), index(0), file(file)
  { }

  System::Region* region;
  unsigned entryCount;
  unsigned indexSize;
  Entry** index;
  int64_t file;
  Entry entries[0];
};

int64_t JNICALL
openZipFile(Thread* t, object method, uintptr_t* arguments)
{
  object path = reinterpret_cast<object>(arguments[0]);
  int mode = arguments[1];
  int64_t lastModified; memcpy(&lastModified, arguments + 2, 8);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  THREAD_RUNTIME_ARRAY(t, char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));
  if (ef.jar) {
    if (ef.jarLength == 0 or ef.pathLength == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    System::Region* r = finder->find(ef.path);
    if (r == 0) {
      throwNew(t, Machine::FileNotFoundExceptionType);
    }

    const uint8_t* start = r->start();
    const uint8_t* end = start + r->length();
    unsigned entryCount = 0;
    for (const uint8_t* p = end - CentralDirectorySearchStart; p > start;) {
      if (get4(p) == CentralDirectorySignature) {
        p = start + centralDirectoryOffset(p);

        while (p < end) {
          if (get4(p) == EntrySignature) {
            ++ entryCount;

            p = endOfEntry(p);
          } else {
            goto make;
          }
        }
      } else {
	-- p;
      }
    }

  make:
    ZipFile* file = new
      (t->m->heap->allocate
       (sizeof(ZipFile) + (sizeof(ZipFile::Entry) * entryCount)))
      ZipFile(t, r, entryCount);

    { unsigned position = 0;
      for (const uint8_t* p = end - CentralDirectorySearchStart; p > start;) {
        if (get4(p) == CentralDirectorySignature) {
          p = start + centralDirectoryOffset(p);

          while (p < end) {
            if (get4(p) == EntrySignature) {
              unsigned h = hash(fileName(p), fileNameLength(p));
              unsigned i = h & (file->indexSize - 1);

              file->index[i] = new (file->entries + (position++))
                ZipFile::Entry(h, p, file->index[i]);

              p = endOfEntry(p);
            } else {
              goto exit;
            }
          }
        } else {
          -- p;
        }
      }
    }

  exit:
    return reinterpret_cast<int64_t>(file);
  } else {
    return reinterpret_cast<int64_t>
      (new (t->m->heap->allocate(sizeof(ZipFile))) ZipFile
       (longValue
        (t, t->m->processor->invoke
         (t, nativeInterceptOriginal
          (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
          0, path, mode, lastModified))));
  }
}

int64_t JNICALL
getZipFileEntryCount(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    return file->entryCount;
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, file->file));
  }
}

ZipFile::Entry*
find(ZipFile* file, const char* path, unsigned pathLength)
{
  unsigned i = hash(path) & (file->indexSize - 1);
  for (ZipFile::Entry* e = file->index[i]; e; e = e->next) {
    const uint8_t* p = e->start;
    if (equal(path, pathLength, fileName(p), fileNameLength(p))) {
      return e;
    }
  }
  return 0;
}

int64_t JNICALL
getZipFileEntry(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  object path = reinterpret_cast<object>(arguments[2]);
  bool addSlash = arguments[3];

  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    THREAD_RUNTIME_ARRAY(t, char, p, byteArrayLength(t, path) + 2);
    memcpy(RUNTIME_ARRAY_BODY(p), &byteArrayBody(t, path, 0),
           byteArrayLength(t, path));
    RUNTIME_ARRAY_BODY(p)[byteArrayLength(t, path)] = 0;
    replace('\\', '/', RUNTIME_ARRAY_BODY(p));
    if (addSlash) {
      RUNTIME_ARRAY_BODY(p)[byteArrayLength(t, path)] = '/';
      RUNTIME_ARRAY_BODY(p)[byteArrayLength(t, path) + 1] = 0;
    }

    return reinterpret_cast<int64_t>(find(file, p, byteArrayLength(t, path)));
  } else {
    int64_t entry = longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, file->file, path, addSlash));

    return entry ? reinterpret_cast<int64_t>
      (new (t->m->heap->allocate(sizeof(ZipFile::Entry)))
       ZipFile::Entry(entry)) : 0;
  }
}

int64_t JNICALL
getZipFileEntryBytes(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int type = arguments[2];

  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(peer);
  if (entry->start) {
    switch (type) {
    case 0: { // name
      unsigned nameLength = fileNameLength(entry->start);
      object array = makeByteArray(t, nameLength + 1);
      memcpy(&byteArrayBody(t, array, 0), fileName(entry->start), nameLength);
      byteArrayBody(t, array, nameLength) = 0;
      return reinterpret_cast<int64_t>(array);
    } break;

    case 1: { // extra
      return 0;
    } break;

    case 2: { // comment
      return 0;
    } break;

    default: abort(t);
    }
    return compressedSize(entry->start);
  } else {
    return reinterpret_cast<int64_t>
      (t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, entry->entry, type));
  }
}

int64_t JNICALL
getNextZipFileEntry(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);
  int index = arguments[2];

  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    return reinterpret_cast<int64_t>(file->entries + index);
  } else {
    int64_t entry = longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, file->file, index));

    return entry ? reinterpret_cast<int64_t>
      (new (t->m->heap->allocate(sizeof(ZipFile::Entry)))
       ZipFile::Entry(entry)) : 0;
  }
}

void JNICALL
initializeZipEntryFields(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  int64_t peer; memcpy(&peer, arguments + 1, 8);

  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(peer);
  if (entry->start) {
    PROTECT(t, this_);

    MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

    unsigned nameLength = fileNameLength(entry->start);
    object array = makeByteArray(t, nameLength + 1);
    memcpy(&byteArrayBody(t, array, 0), fileName(entry->start), nameLength);
    byteArrayBody(t, array, nameLength) = 0;

    object name = t->m->classpath->makeString
      (t, array, 0, byteArrayLength(t, array) - 1);

    set(t, this_, cp->zipEntryNameField, name);

    cast<int64_t>(this_, cp->zipEntryTimeField)
      = fileTime(entry->start);
    cast<int64_t>(this_, cp->zipEntryCrcField)
      = fileCRC(entry->start);
    cast<int64_t>(this_, cp->zipEntrySizeField)
      = uncompressedSize(entry->start);
    cast<int64_t>(this_, cp->zipEntryCsizeField)
      = compressedSize(entry->start);
    cast<int64_t>(this_, cp->zipEntryMethodField)
      = compressionMethod(entry->start);
  } else {
    t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_, entry->entry);
  }
}

int64_t JNICALL
getZipFileEntryMethod(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(peer);
  if (entry->start) {
    return compressionMethod(entry->start);
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, entry->entry));
  }
}

int64_t JNICALL
getZipFileEntryCompressedSize(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(peer);
  if (entry->start) {
    return compressedSize(entry->start);
  } else {
    return longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, entry->entry));
  }
}

int64_t JNICALL
getZipFileEntryUncompressedSize(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(peer);
  if (entry->start) {
    return uncompressedSize(entry->start);
  } else {
    return longValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, entry->entry));
  }
}

void JNICALL
freeZipFileEntry(Thread* t, object method, uintptr_t* arguments)
{
  int64_t filePeer; memcpy(&filePeer, arguments, 8);
  int64_t entryPeer; memcpy(&entryPeer, arguments + 2, 8);

  ZipFile* file = reinterpret_cast<ZipFile*>(filePeer);
  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(entryPeer);
  if (file->region == 0) {
    t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       0, file->file, entry->entry);
  }

  t->m->heap->free(entry, sizeof(ZipFile::Entry));
}

int64_t JNICALL
readZipFileEntry(Thread* t, object method, uintptr_t* arguments)
{
  int64_t filePeer; memcpy(&filePeer, arguments, 8);
  int64_t entryPeer; memcpy(&entryPeer, arguments + 2, 8);
  int64_t position; memcpy(&position, arguments + 4, 8);
  object buffer = reinterpret_cast<object>(arguments[6]);
  int offset = arguments[7];
  int length = arguments[8];

  ZipFile* file = reinterpret_cast<ZipFile*>(filePeer);
  ZipFile::Entry* entry = reinterpret_cast<ZipFile::Entry*>(entryPeer);
  if (file->region) {
    unsigned size = uncompressedSize(entry->start);
    if (position >= size) {
      return -1;
    }

    if (position + length > size) {
      length = size - position;
    }

    memcpy(&byteArrayBody(t, buffer, offset),
           fileData(file->region->start() + localHeaderOffset(entry->start))
           + position,
           length);

    return length;
  } else {
    return intValue
      (t, t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, file->file, entry->entry, position, buffer, offset, length));
  }
}

int64_t JNICALL
getZipMessage(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    return 0;
  } else {
    return reinterpret_cast<int64_t>
      (t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        0, file->file));
  }
}

int64_t JNICALL
getJarFileMetaInfEntryNames(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  int64_t peer = cast<int64_t>(this_, cp->zipFileJzfileField);
  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    return 0;
  } else {
    PROTECT(t, method);

    // OpenJDK's Java_java_util_jar_JarFile_getMetaInfEntryNames
    // implementation expects to find a pointer to an instance of its
    // jzfile structure in the ZipFile.jzfile field of the object we
    // pass in.  However, we can't pass this_ in, because its
    // ZipFile.jzfile field points to a ZipFile instance, not a
    // jzfile.  So we pass in a temporary object instead which has the
    // desired pointer at the same offset.  We assume here that
    // ZipFile.jzfile is the first field in that class and that
    // Java_java_util_jar_JarFile_getMetaInfEntryNames will not look
    // for any other fields in the object.
    object pseudoThis = makeLong(t, file->file);

    return reinterpret_cast<int64_t>
      (t->m->processor->invoke
       (t, nativeInterceptOriginal
        (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
        pseudoThis));
  }
}

void JNICALL
closeZipFile(Thread* t, object method, uintptr_t* arguments)
{
  int64_t peer; memcpy(&peer, arguments, 8);

  ZipFile* file = reinterpret_cast<ZipFile*>(peer);
  if (file->region) {
    file->region->dispose();
    t->m->heap->free(file, sizeof(ZipFile)
                     + (sizeof(ZipFile::Entry) * file->entryCount));
  } else {
    t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       0, file->file);

    t->m->heap->free(file, sizeof(ZipFile));
  }
}

int64_t JNICALL
getBootstrapResource(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  object m = findMethodOrNull
    (t, type(t, Machine::SystemClassLoaderType),
     "findResource", "(Ljava/lang/String;)Ljava/net/URL;");
  
  if (m) {
    return reinterpret_cast<int64_t>
      (t->m->processor->invoke(t, m, root(t, Machine::BootLoader), name));
  } else {
    return 0;
  }
}

int64_t JNICALL
getBootstrapResources(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[0]);
  PROTECT(t, name);

  object m = findMethodOrNull
    (t, type(t, Machine::SystemClassLoaderType),
     "findResources", "(Ljava/lang/String;)Ljava/util/Enumeration;");
  
  if (m) {
    return reinterpret_cast<int64_t>
      (t->m->processor->invoke(t, m, root(t, Machine::BootLoader), name));
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jint JNICALL
net_JNI_OnLoad(JavaVM*, void*);

extern "C" JNIEXPORT jint JNICALL
management_JNI_OnLoad(JavaVM*, void*);

void JNICALL
loadLibrary(Thread* t, object, uintptr_t* arguments)
{
  object name = reinterpret_cast<object>(arguments[1]);
  THREAD_RUNTIME_ARRAY(t, char, n, stringLength(t, name) + 1);
  stringChars(t, name, RUNTIME_ARRAY_BODY(n));

  bool absolute = arguments[2];

#ifdef AVIAN_OPENJDK_SRC
  if (not absolute) {
    if (strcmp(n, "net") == 0) {
      bool ran;

      { ACQUIRE(t, t->m->classLock);

        local::MyClasspath* c = static_cast<local::MyClasspath*>
          (t->m->classpath);

        ran = c->ranNetOnLoad;
        c->ranNetOnLoad = true;
      }

      if (not ran) {
        net_JNI_OnLoad(t->m, 0);
      }

      return;
    } else if (strcmp(n, "management") == 0) { 
      bool ran;

      { ACQUIRE(t, t->m->classLock);

        local::MyClasspath* c = static_cast<local::MyClasspath*>
          (t->m->classpath);

        ran = c->ranManagementOnLoad;
        c->ranManagementOnLoad = true;
      }

      if (not ran) {
        management_JNI_OnLoad(t->m, 0);
      }

      return;     
    } else if (strcmp(n, "zip") == 0
               or strcmp(n, "nio") == 0)
    {
      return;
    }
  }
#endif // AVIAN_OPENJDK_SRC 
 
  loadLibrary
    (t, static_cast<local::MyClasspath*>(t->m->classpath)->libraryPath,
     RUNTIME_ARRAY_BODY(n), not absolute, true);
}

// only safe to call during bootstrap when there's only one thread
// running:
void
intercept(Thread* t, object c, const char* name, const char* spec,
          void* function)
{
  object m = findMethodOrNull(t, c, name, spec);
  if (m) {
    PROTECT(t, m);

    object clone = methodClone(t, m);

    // make clone private to prevent vtable updates at compilation
    // time.  Otherwise, our interception might be bypassed by calls
    // through the vtable.
    methodFlags(t, clone) |= ACC_PRIVATE;

    methodFlags(t, m) |= ACC_NATIVE;

    object native = makeNativeIntercept(t, function, true, clone);

    PROTECT(t, native);

    object runtimeData = getMethodRuntimeData(t, m);

    set(t, runtimeData, MethodRuntimeDataNative, native);
  } else {
    // If we can't find the method, just ignore it, since ProGuard may
    // have stripped it out as unused.  Otherwise, the code below can
    // be uncommented for debugging purposes.

    // fprintf(stderr, "unable to find %s%s in %s\n",
    //         name, spec, &byteArrayBody(t, className(t, c), 0));

    // abort(t);
  }
}

void
interceptFileOperations(Thread* t)
{
  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  { object fileClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/File", false);

    if (fileClass) {
      object filePathField = findFieldInClass2
        (t, fileClass, "path", "Ljava/lang/String;");
      
      if (filePathField) {
        cp->filePathField = fieldOffset(t, filePathField);
      }
    }
  }

  { object fileDescriptorClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/FileDescriptor", false);

    if (fileDescriptorClass) {
      object fileDescriptorFdField = findFieldInClass2
        (t, fileDescriptorClass, "fd", "I");

      if (fileDescriptorFdField) {
        cp->fileDescriptorFdField = fieldOffset(t, fileDescriptorFdField);
      }
    }
  }

  { object fileInputStreamClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/FileInputStream", false);

    if (fileInputStreamClass) {
      PROTECT(t, fileInputStreamClass);

      object fileInputStreamFdField = findFieldInClass2
        (t, fileInputStreamClass, "fd", "Ljava/io/FileDescriptor;");

      if (fileInputStreamFdField) {
        cp->fileInputStreamFdField = fieldOffset(t, fileInputStreamFdField);

        intercept(t, fileInputStreamClass, "open", "(Ljava/lang/String;)V",
                  voidPointer(openFile));
  
        intercept(t, fileInputStreamClass, "read", "()I",
                  voidPointer(readByteFromFile));
  
        intercept(t, fileInputStreamClass, "readBytes", "([BII)I",
                  voidPointer(readBytesFromFile));
  
        intercept(t, fileInputStreamClass, "skip", "(J)J",
                  voidPointer(skipBytesInFile));
  
        intercept(t, fileInputStreamClass, "available", "()I",
                  voidPointer(availableBytesInFile));
  
        intercept(t, fileInputStreamClass, "close0", "()V",
                  voidPointer(closeFile));
      }
    }
  }

  { object zipFileClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/util/zip/ZipFile", false);

    if (zipFileClass) {
      PROTECT(t, zipFileClass);

      object zipFileJzfileField = findFieldInClass2
        (t, zipFileClass, "jzfile", "J");

      if (zipFileJzfileField) {
        cp->zipFileJzfileField = fieldOffset(t, zipFileJzfileField);

        intercept(t, zipFileClass, "open", "(Ljava/lang/String;IJZ)J",
                  voidPointer(openZipFile));

        intercept(t, zipFileClass, "getTotal", "(J)I",
                  voidPointer(getZipFileEntryCount));

        intercept(t, zipFileClass, "getEntry", "(J[BZ)J",
                  voidPointer(getZipFileEntry));

        intercept(t, zipFileClass, "getEntryBytes", "(JI)[B",
                  voidPointer(getZipFileEntryBytes));

        intercept(t, zipFileClass, "getNextEntry", "(JI)J",
                  voidPointer(getNextZipFileEntry));

        intercept(t, zipFileClass, "getEntryMethod", "(J)I",
                  voidPointer(getZipFileEntryMethod));

        intercept(t, zipFileClass, "freeEntry", "(JJ)V",
                  voidPointer(freeZipFileEntry));

        intercept(t, zipFileClass, "read", "(JJJ[BII)I",
                  voidPointer(readZipFileEntry));

        intercept(t, zipFileClass, "getEntryCSize", "(J)J",
                  voidPointer(getZipFileEntryCompressedSize));

        intercept(t, zipFileClass, "getEntrySize", "(J)J",
                  voidPointer(getZipFileEntryUncompressedSize));

        intercept(t, zipFileClass, "getZipMessage", "(J)Ljava/lang/String;",
                  voidPointer(getZipMessage));

        intercept(t, zipFileClass, "close", "(J)V",
                  voidPointer(closeZipFile));
      }
    }
  }

  { object jarFileClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/util/jar/JarFile", false);

    if (jarFileClass) {
      intercept(t, jarFileClass, "getMetaInfEntryNames",
                "()[Ljava/lang/String;",
                voidPointer(getJarFileMetaInfEntryNames));
    }
  }

  {
#ifdef PLATFORM_WINDOWS
    const char* const fsClassName = "java/io/WinNTFileSystem";
    const char* const gbaMethodName = "getBooleanAttributes";
#else
    const char* const fsClassName = "java/io/UnixFileSystem";
    const char* const gbaMethodName = "getBooleanAttributes0";
#endif

    object fsClass = resolveClass
      (t, root(t, Machine::BootLoader), fsClassName, false);

    if (fsClass) {
      PROTECT(t, fsClass);

      intercept(t, fsClass, gbaMethodName, "(Ljava/io/File;)I",
                voidPointer(getFileAttributes));

      intercept(t, fsClass, "checkAccess", "(Ljava/io/File;I)Z",
                voidPointer(checkFileAccess));
  
      intercept(t, fsClass, "getLength", "(Ljava/io/File;)J",
                voidPointer(getFileLength));
    }
  }

  intercept(t, type(t, Machine::ClassLoaderType), "loadLibrary",
            "(Ljava/lang/Class;Ljava/lang/String;Z)V",
            voidPointer(loadLibrary));

  intercept(t, type(t, Machine::ClassLoaderType), "getBootstrapResource",
            "(Ljava/lang/String;)Ljava/net/URL;",
            voidPointer(getBootstrapResource));

  intercept(t, type(t, Machine::ClassLoaderType), "getBootstrapResources",
            "(Ljava/lang/String;)Ljava/util/Enumeration;",
            voidPointer(getBootstrapResources));
}

object
getClassMethodTable(Thread* t, object c)
{
  object addendum = classAddendum(t, c);
  if (addendum) {
    object table = classAddendumMethodTable(t, addendum);
    if (table) {
      return table;
    }
  }
  return classMethodTable(t, c);
}

unsigned
countMethods(Thread* t, object c, bool publicOnly)
{
  object table = getClassMethodTable(t, c);
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
    unsigned count = 0;
    for (unsigned i = 0; i < arrayLength(t, table); ++i) {
      object vmField = arrayBody(t, table, i);
      if (fieldFlags(t, vmField) & ACC_PUBLIC) {
        ++ count;
      }
    }
    return count;
  } else {
    return objectArrayLength(t, table);
  }
}

unsigned
countConstructors(Thread* t, object c, bool publicOnly)
{
  object table = getClassMethodTable(t, c);
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
    THREAD_RUNTIME_ARRAY(t, char, s, specLength - 1);
    memcpy(RUNTIME_ARRAY_BODY(s), spec + 1, specLength - 2);
    RUNTIME_ARRAY_BODY(s)[specLength - 2] = 0;
    return resolveClass(t, loader, s);
  }
  
  case '[': {
    THREAD_RUNTIME_ARRAY(t, char, s, specLength + 1);
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
  return getJClass(t, resolveClassBySpec(t, loader, spec, specLength));
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

  PROTECT(t, list);
  
  object array = makeObjectArray
    (t, type(t, Machine::JclassType), *parameterCount);
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
  if (addendum == 0 or methodAddendumExceptionTable(t, addendum) == 0) {
    return makeObjectArray(t, type(t, Machine::JclassType), 0);
  }

  PROTECT(t, loader);
  PROTECT(t, addendum);

  object array = makeObjectArray
    (t, type(t, Machine::JclassType),
     shortArrayLength(t, methodAddendumExceptionTable(t, addendum)));
  PROTECT(t, array);

  for (unsigned i = 0; i < shortArrayLength
         (t, methodAddendumExceptionTable(t, addendum)); ++i)
  {
    uint16_t index = shortArrayBody
      (t, methodAddendumExceptionTable(t, addendum), i) - 1;

    object o = singletonObject(t, addendumPool(t, addendum), index);

    if (objectClass(t, o) == type(t, Machine::ReferenceType)) {
      o = resolveClass(t, loader, referenceName(t, o));
    
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

object
interruptLock(Thread* t, object thread)
{
  object lock = threadInterruptLock(t, thread);

  loadMemoryBarrier();

  if (lock == 0) {
    PROTECT(t, thread);
    ACQUIRE(t, t->m->referenceLock);

    if (threadInterruptLock(t, thread) == 0) {
      object head = makeMonitorNode(t, 0, 0);
      object lock = makeMonitor(t, 0, 0, 0, head, head, 0);

      storeStoreMemoryBarrier();

      set(t, thread, ThreadInterruptLock, lock);
    }
  }
  
  return threadInterruptLock(t, thread);
}

void
clearInterrupted(Thread* t)
{
  monitorAcquire(t, local::interruptLock(t, t->javaThread));
  threadInterrupted(t, t->javaThread) = false;
  monitorRelease(t, local::interruptLock(t, t->javaThread));
}

bool
pipeAvailable(int fd, int* available)
{
#ifdef PLATFORM_WINDOWS
  HANDLE h = reinterpret_cast<HANDLE>(_get_osfhandle(fd));
  if (h == INVALID_HANDLE_VALUE) {
    return false;
  }

  DWORD n;
  if (PeekNamedPipe(h, 0,0, 0, &n, 0)) {
    *available = n;
  } else {
    if (GetLastError() != ERROR_BROKEN_PIPE) {
      return false;
    }
    *available = 0;
  }

  return true;
#else
  return ioctl(fd, FIONREAD, available) >= 0;
#endif
}

object
fieldForOffsetInClass(Thread* t, object c, unsigned offset)
{
  object super = classSuper(t, c);
  if (super) {
    object field = fieldForOffsetInClass(t, super, offset);
    if (field) {
      return field;
    }
  }

  object table = classFieldTable(t, c);
  if (table) {
    for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
      object field = objectArrayBody(t, table, i);
      if ((fieldFlags(t, field) & ACC_STATIC) == 0
          and fieldOffset(t, field) == offset)
      {
        return field;
      }
    }
  }

  return 0;
}

object
fieldForOffset(Thread* t, object o, unsigned offset)
{
  object c = objectClass(t, o);
  if (classVmFlags(t, c) & SingletonFlag) {
    c = singletonObject(t, o, 0);
    object table = classFieldTable(t, c);
    if (table) {
      for (unsigned i = 0; i < objectArrayLength(t, table); ++i) {
        object field = objectArrayBody(t, table, i);
        if ((fieldFlags(t, field) & ACC_STATIC)
            and fieldOffset(t, field) == offset)
        {
          return field;
        }
      }
    }
    abort(t);
  } else {
    object field = fieldForOffsetInClass(t, c, offset);
    if (field) {
      return field;
    } else {
      abort(t);
    }
  }
}

} // namespace local

} // namespace

namespace vm {

Classpath*
makeClasspath(System* s, Allocator* allocator, const char* javaHome,
              const char* embedPrefix)
{
  return new (allocator->allocate(sizeof(local::MyClasspath)))
    local::MyClasspath(s, allocator, javaHome, embedPrefix);
}

} // namespace vm

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_lang_Class_getSuperclass
(Thread* t, object, uintptr_t* arguments)
{
  object class_ = jclassVmClass(t, reinterpret_cast<object>(arguments[0]));
  if (classFlags(t, class_) & ACC_INTERFACE) {
    return 0;
  } else {
    object super = classSuper(t, class_);
    return super ? reinterpret_cast<int64_t>(getJClass(t, super)) : 0;
  }
}

extern "C" JNIEXPORT void
Avian_sun_misc_Unsafe_registerNatives
(Thread*, object, uintptr_t*)
{
  // ignore
}

extern "C" JNIEXPORT void
Avian_sun_misc_Perf_registerNatives
(Thread*, object, uintptr_t*)
{
  // ignore
}

extern "C" JNIEXPORT int64_t
Avian_sun_misc_Perf_createLong
(Thread* t, object, uintptr_t*)
{
  return reinterpret_cast<int64_t>
    (t->m->processor->invoke
     (t, resolveMethod
      (t, root(t, Machine::BootLoader), "java/nio/ByteBuffer", "allocate",
       "(I)Ljava/nio/ByteBuffer;"), 0, 8));
}

extern "C" JNIEXPORT int64_t
Avian_sun_misc_Unsafe_addressSize
(Thread*, object, uintptr_t*)
{
  return BytesPerWord;
}

extern "C" JNIEXPORT int64_t
Avian_sun_misc_Unsafe_defineClass__Ljava_lang_String_2_3BIILjava_lang_ClassLoader_2Ljava_security_ProtectionDomain_2
(Thread* t, object, uintptr_t* arguments)
{
  //object name = reinterpret_cast<object>(arguments[1]);
  object data = reinterpret_cast<object>(arguments[2]);
  int32_t offset = arguments[3];
  int32_t length = arguments[4];
  object loader = reinterpret_cast<object>(arguments[5]);
  //object domain = reinterpret_cast<object>(arguments[6]);

  uint8_t* buffer = static_cast<uint8_t*>(t->m->heap->allocate(length));

  THREAD_RESOURCE2(t, uint8_t*, buffer, int, length,
                   t->m->heap->free(buffer, length));

  memcpy(buffer, &byteArrayBody(t, data, offset), length);

  return reinterpret_cast<int64_t>
    (getJClass(t, defineClass(t, loader, buffer, length)));
}

extern "C" JNIEXPORT int64_t
Avian_sun_misc_Unsafe_allocateInstance
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[1]));
  PROTECT(t, c);

  initClass(t, c);

  return reinterpret_cast<int64_t>(make(t, c));
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
Avian_sun_misc_Unsafe_arrayIndexScale
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[1]));

  if (classVmFlags(t, c) & PrimitiveFlag) {
    const char* name = reinterpret_cast<char*>
      (&byteArrayBody(t, local::getClassName(t, c), 0));

    switch (*name) {
    case 'b': return 1;
    case 's':
    case 'c': return 2;
    case 'l':
    case 'd': return 8;
    case 'i':
    case 'f': return 4;
    default: abort(t);
    }
  } else {
    return BytesPerWord;
  }
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
Avian_sun_misc_Unsafe_getObject
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<uintptr_t>(o, offset);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putObject
(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uintptr_t value = arguments[4];

  set(t, o, offset, reinterpret_cast<object>(value));
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getShort__Ljava_lang_Object_2J
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<int16_t>(o, offset);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getInt__Ljava_lang_Object_2J
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<int32_t>(o, offset);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getFloat__Ljava_lang_Object_2J
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<int32_t>(o, offset);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getIntVolatile
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  int32_t result = cast<int32_t>(o, offset);
  loadMemoryBarrier();
  return result;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getLong__Ljava_lang_Object_2J
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<int64_t>(o, offset);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getDouble__Ljava_lang_Object_2J
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getLong__Ljava_lang_Object_2J
    (t, method, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getLongVolatile
(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  object field;
  if (BytesPerWord < 8) {
    field = local::fieldForOffset(t, o, offset);

    PROTECT(t, field);
    acquire(t, field);        
  }

  int64_t result = cast<int64_t>(o, offset);

  if (BytesPerWord < 8) {
    release(t, field);        
  } else {
    loadMemoryBarrier();
  }

  return result;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putByte__Ljava_lang_Object_2JB
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int8_t value = arguments[4];

  cast<int8_t>(o, offset) = value;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putShort__Ljava_lang_Object_2JS
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int16_t value = arguments[4];

  cast<int16_t>(o, offset) = value;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putInt__Ljava_lang_Object_2JI
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int32_t value = arguments[4];

  cast<int32_t>(o, offset) = value;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putFloat__Ljava_lang_Object_2JF
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int32_t value = arguments[4];

  cast<int32_t>(o, offset) = value;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getByte__Ljava_lang_Object_2J
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<int8_t>(o, offset);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getBoolean__Ljava_lang_Object_2J
(Thread* t, object method, uintptr_t* arguments)
{
  return Avian_sun_misc_Unsafe_getByte__Ljava_lang_Object_2J
    (t, method, arguments);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putBoolean__Ljava_lang_Object_2JZ
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uint8_t value = arguments[4];

  cast<uint8_t>(o, offset) = value;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putLong__Ljava_lang_Object_2JJ
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int64_t value; memcpy(&value, arguments + 4, 8);

  cast<int64_t>(o, offset) = value;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getObjectVolatile
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  
  uintptr_t value = cast<uintptr_t>(o, offset);
  loadMemoryBarrier();
  return value;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putObjectVolatile
(Thread* t, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  object value = reinterpret_cast<object>(arguments[4]);
  
  storeStoreMemoryBarrier();
  set(t, o, offset, reinterpret_cast<object>(value));
  storeLoadMemoryBarrier();
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putOrderedObject
(Thread* t, object method, uintptr_t* arguments)
{
  Avian_sun_misc_Unsafe_putObjectVolatile(t, method, arguments);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapInt
(Thread*, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uint32_t expect = arguments[4];
  uint32_t update = arguments[5];

  return atomicCompareAndSwap32
    (&cast<uint32_t>(target, offset), expect, update);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapObject
(Thread* t, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uintptr_t expect = arguments[4];
  uintptr_t update = arguments[5];

  bool success = atomicCompareAndSwap
    (&cast<uintptr_t>(target, offset), expect, update);

  if (success) {
    mark(t, target, offset);
  }

  return success;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_compareAndSwapLong
(Thread* t UNUSED, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uint64_t expect; memcpy(&expect, arguments + 4, 8);
  uint64_t update; memcpy(&update, arguments + 6, 8);

#ifdef AVIAN_HAS_CAS64
  return atomicCompareAndSwap64
    (&cast<uint64_t>(target, offset), expect, update);
#else
  ACQUIRE_FIELD_FOR_WRITE(t, local::fieldForOffset(t, target, offset));
  if (cast<uint64_t>(target, offset) == expect) {
    cast<uint64_t>(target, offset) = update;
    return true;
  } else {
    return false;
  }
#endif
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_pageSize
(Thread*, object, uintptr_t*)
{
  return local::PageSize;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_ensureClassInitialized
(Thread* t, object, uintptr_t* arguments)
{
  initClass(t, jclassVmClass(t, reinterpret_cast<object>(arguments[1])));
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_unpark
(Thread* t, object, uintptr_t* arguments)
{
  object thread = reinterpret_cast<object>(arguments[1]);
  
  monitorAcquire(t, local::interruptLock(t, thread));
  threadUnparked(t, thread) = true;
  monitorNotify(t, local::interruptLock(t, thread));
  monitorRelease(t, local::interruptLock(t, thread));
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_park
(Thread* t, object, uintptr_t* arguments)
{
  bool absolute = arguments[1];
  int64_t time; memcpy(&time, arguments + 2, 8);
  
  int64_t then = t->m->system->now();

  if (absolute) {
    time -= then;
    if (time <= 0) {
      return;
    }
  } else if (time) {
    // if not absolute, interpret time as nanoseconds, but make sure
    // it doesn't become zero when we convert to milliseconds, since
    // zero is interpreted as infinity below
    time = (time / (1000 * 1000)) + 1;
  }

  monitorAcquire(t, local::interruptLock(t, t->javaThread));
  while (time >= 0
         and (not (threadUnparked(t, t->javaThread)
                   or monitorWait
                   (t, local::interruptLock(t, t->javaThread), time))))
  {
    int64_t now = t->m->system->now();
    time -= now - then;
    then = now;
    
    if (time == 0) {
      break;
    }
  }
  threadUnparked(t, t->javaThread) = false;
  monitorRelease(t, local::interruptLock(t, t->javaThread));
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_monitorEnter
(Thread* t, object, uintptr_t* arguments)
{
  acquire(t, reinterpret_cast<object>(arguments[1]));
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_monitorExit
(Thread* t, object, uintptr_t* arguments)
{
  release(t, reinterpret_cast<object>(arguments[1]));
}

namespace {

namespace local {

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetInterfaceVersion)()
{
  return local::InterfaceVersion;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_IHashCode)(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return o ? objectHash(t, *o) : 0;
}

uint64_t
jvmWait(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);
  jlong milliseconds; memcpy(&milliseconds, arguments + 1, sizeof(jlong));

  vm::wait(t, *o, milliseconds);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorWait)(Thread* t, jobject o, jlong milliseconds)
{
  uintptr_t arguments[1 + (sizeof(jlong) / BytesPerWord)];
  arguments[0] = reinterpret_cast<uintptr_t>(o);
  memcpy(arguments + 1, &milliseconds, sizeof(jlong));

  run(t, jvmWait, arguments);
}

uint64_t
jvmNotify(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);

  notify(t, *o);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorNotify)(Thread* t, jobject o)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(o) };

  run(t, jvmNotify, arguments);
}

uint64_t
jvmNotifyAll(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);

  notifyAll(t, *o);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorNotifyAll)(Thread* t, jobject o)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(o) };

  run(t, jvmNotifyAll, arguments);
}

uint64_t
jvmClone(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);

  return reinterpret_cast<uint64_t>(makeLocalReference(t, clone(t, *o)));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_Clone)(Thread* t, jobject o)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(o) };

  return reinterpret_cast<jobject>(run(t, jvmClone, arguments));
}

uint64_t
jvmInternString(Thread* t, uintptr_t* arguments)
{
  jobject o = reinterpret_cast<jobject>(arguments[0]);

  return reinterpret_cast<uint64_t>(makeLocalReference(t, intern(t, *o)));
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_InternString)(Thread* t, jstring s)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(s) };

  return reinterpret_cast<jobject>(run(t, jvmInternString, arguments));
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_CurrentTimeMillis)(Thread* t, jclass)
{
  return t->m->system->now();
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_NanoTime)(Thread* t, jclass)
{
  return t->m->system->now() * 1000 * 1000;
}

uint64_t
jvmArrayCopy(Thread* t, uintptr_t* arguments)
{
  jobject src = reinterpret_cast<jobject>(arguments[0]);
  jint srcOffset = arguments[1];
  jobject dst = reinterpret_cast<jobject>(arguments[2]);
  jint dstOffset = arguments[3];
  jint length = arguments[4];

  arrayCopy(t, *src, srcOffset, *dst, dstOffset, length);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ArrayCopy)(Thread* t, jclass, jobject src, jint srcOffset,
                      jobject dst, jint dstOffset, jint length)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(src),
                            static_cast<uintptr_t>(srcOffset),
                            reinterpret_cast<uintptr_t>(dst),
                            static_cast<uintptr_t>(dstOffset),
                            static_cast<uintptr_t>(length) };

  run(t, jvmArrayCopy, arguments);
}

uint64_t
jvmInitProperties(Thread* t, uintptr_t* arguments)
{
  jobject properties = reinterpret_cast<jobject>(arguments[0]);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "java/util/Properties", "setProperty",
     "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

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
#else // not PLATFORM_WINDOWS
  local::setProperty(t, method, *properties, "line.separator", "\n");
  local::setProperty(t, method, *properties, "file.separator", "/");
  local::setProperty(t, method, *properties, "path.separator", ":");
#  ifdef __APPLE__
  local::setProperty(t, method, *properties, "os.name", "Mac OS X");
#  elif defined __FreeBSD__
  local::setProperty(t, method, *properties, "os.name", "FreeBSD");
#  else // not __APPLE__
  local::setProperty(t, method, *properties, "os.name", "Linux");
#  endif // not __APPLE__
  local::setProperty(t, method, *properties, "java.io.tmpdir", "/tmp");
  local::setProperty(t, method, *properties, "user.home", getenv("HOME"));

  char buffer[PATH_MAX];
  local::setProperty(t, method, *properties, "user.dir",
                     getcwd(buffer, PATH_MAX));
#endif // not PLATFORM_WINDOWS

  local::setProperty(t, method, *properties, "java.protocol.handler.pkgs",
                     "avian");

  local::setProperty(t, method, *properties, "java.vm.vendor",
                     "Avian Contributors");

  local::setProperty(t, method, *properties, "java.vm.name","Avian");
#ifdef AVIAN_VERSION
  local::setProperty(t, method, *properties, "java.vm.version",AVIAN_VERSION);
#endif
#ifdef AVIAN_INFO
  local::setProperty(t, method, *properties, "java.vm.info",AVIAN_INFO);
#endif

  local::setProperty
    (t, method, *properties, "java.home",
     static_cast<local::MyClasspath*>(t->m->classpath)->javaHome);

  local::setProperty
    (t, method, *properties, "sun.boot.library.path",
     static_cast<local::MyClasspath*>(t->m->classpath)->libraryPath);

  local::setProperty
    (t, method, *properties, "sun.boot.class.path",
     static_cast<Finder*>
     (systemClassLoaderFinder(t, root(t, Machine::BootLoader)))->path());

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
      THREAD_RUNTIME_ARRAY(t, char, name, (p - start) + 1);
      memcpy(name, start, p - start);
      name[p - start] = 0;
      local::setProperty
        (t, method, *properties, RUNTIME_ARRAY_BODY(name), p + 1);
    }
  }  

  return reinterpret_cast<uint64_t>(properties);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InitProperties)(Thread* t, jobject properties)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(properties) };

  return reinterpret_cast<jobject>(run(t, jvmInitProperties, arguments));
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_OnExit)(void (*)(void)) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Exit)(jint code)
{
  exit(code);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Halt)(jint code)
{
  exit(code);
}

uint64_t
jvmGC(Thread* t, uintptr_t*)
{
  collect(t, Heap::MajorCollection);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GC)()
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  run(t, jvmGC, 0);
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_MaxObjectInspectionAge)(void)
{
  return 0;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_TraceInstructions)(jboolean) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_TraceMethodCalls)(jboolean) { abort(); }

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_TotalMemory)()
{
  return 0;
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_FreeMemory)()
{
  return 0;
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_MaxMemory)()
{
  return local::globalMachine->heap->limit();
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ActiveProcessorCount)()
{
#ifdef PLATFORM_WINDOWS
  SYSTEM_INFO si;
  GetSystemInfo(&si);
  return si.dwNumberOfProcessors;
#else
  return sysconf(_SC_NPROCESSORS_ONLN);
#endif
}

uint64_t
jvmLoadLibrary(Thread* t, uintptr_t* arguments)
{
  const char* path = reinterpret_cast<const char*>(arguments[0]);

  THREAD_RUNTIME_ARRAY(t, char, p, strlen(path) + 1);
  replace('\\', '/', RUNTIME_ARRAY_BODY(p), path);

  return reinterpret_cast<uint64_t>
    (loadLibrary
     (t, static_cast<local::MyClasspath*>(t->m->classpath)->libraryPath,
      RUNTIME_ARRAY_BODY(p), false, false));
}

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_LoadLibrary)(const char* path)
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(path) };

  return reinterpret_cast<void*>(run(t, jvmLoadLibrary, arguments));  
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_UnloadLibrary)(void*) { abort(); }

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_FindLibraryEntry)(void* library, const char* name)
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  ENTER(t, Thread::ActiveState);

  if (library == RTLD_DEFAULT) {
    library = t->m->libraries;
  }

  return static_cast<System::Library*>(library)->resolve(name);
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsSupportedJNIVersion)(jint version)
{
  return version <= JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsNaN)(jdouble) { abort(); }

uint64_t
jvmFillInStackTrace(Thread* t, uintptr_t* arguments)
{
  jobject throwable = reinterpret_cast<jobject>(arguments[0]);

  object trace = getTrace(t, 2);
  set(t, *throwable, ThrowableTrace, trace);

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_FillInStackTrace)(Thread* t, jobject throwable)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(throwable) };

  run(t, jvmFillInStackTrace, arguments);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_PrintStackTrace)(Thread*, jobject, jobject) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetStackTraceDepth)(Thread* t, jobject throwable)
{
  ENTER(t, Thread::ActiveState);

  return objectArrayLength(t, throwableTrace(t, *throwable));
}

uint64_t
jvmGetStackTraceElement(Thread* t, uintptr_t* arguments)
{
  jobject throwable = reinterpret_cast<jobject>(arguments[0]);
  jint index = arguments[1];

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, makeStackTraceElement
      (t, objectArrayBody(t, throwableTrace(t, *throwable), index))));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetStackTraceElement)(Thread* t, jobject throwable, jint index)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(throwable),
                            static_cast<uintptr_t>(index) };

  return reinterpret_cast<jobject>(run(t, jvmGetStackTraceElement, arguments));
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_InitializeCompiler) (Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsSilentCompiler)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CompileClass)(Thread*, jclass, jclass)
{
  return false;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CompileClasses)(Thread*, jclass, jstring)
{
  return false;
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_CompilerCommand)(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_EnableCompiler)(Thread*, jclass)
{
  // ignore
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_DisableCompiler)(Thread*, jclass)
{
  // ignore
}

uint64_t
jvmStartThread(Thread* t, uintptr_t* arguments)
{
  jobject thread = reinterpret_cast<jobject>(arguments[0]);

  return startThread(t, *thread) != 0;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_StartThread)(Thread* t, jobject thread)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(thread) };

  run(t, jvmStartThread, arguments);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_StopThread)(Thread*, jobject, jobject) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsThreadAlive)(Thread* t, jobject thread)
{
  ENTER(t, Thread::ActiveState);

  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *thread));
  return p and (p->flags & Thread::ActiveFlag) != 0;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SuspendThread)(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ResumeThread)(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetThreadPriority)(Thread*, jobject, jint)
{
  // ignore
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Yield)(Thread* t, jclass)
{
  t->m->system->yield();
}

uint64_t
jvmSleep(Thread* t, uintptr_t* arguments)
{
  jlong milliseconds; memcpy(&milliseconds, arguments, sizeof(jlong));

  if (milliseconds <= 0) {
    milliseconds = 1;
  }

  if (threadSleepLock(t, t->javaThread) == 0) {
    object lock = makeJobject(t);
    set(t, t->javaThread, ThreadSleepLock, lock);
  }

  acquire(t, threadSleepLock(t, t->javaThread));
  vm::wait(t, threadSleepLock(t, t->javaThread), milliseconds);
  release(t, threadSleepLock(t, t->javaThread));

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Sleep)(Thread* t, jclass, jlong milliseconds)
{
  uintptr_t arguments[sizeof(jlong) / BytesPerWord];
  memcpy(arguments, &milliseconds, sizeof(jlong));

  run(t, jvmSleep, arguments);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_CurrentThread)(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->javaThread);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_CountStackFrames)(Thread*, jobject) { abort(); }

uint64_t
jvmInterrupt(Thread* t, uintptr_t* arguments)
{
  jobject thread = reinterpret_cast<jobject>(arguments[0]);

  monitorAcquire(t, local::interruptLock(t, *thread));
  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *thread));
  if (p) {
    interrupt(t, p);
  }
  threadInterrupted(t, *thread) = true;
  monitorRelease(t, local::interruptLock(t, *thread));

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Interrupt)(Thread* t, jobject thread)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(thread) };

  run(t, jvmInterrupt, arguments);
}

uint64_t
jvmIsInterrupted(Thread* t, uintptr_t* arguments)
{
  jobject thread = reinterpret_cast<jobject>(arguments[0]);
  jboolean clear = arguments[1];

  monitorAcquire(t, local::interruptLock(t, *thread));
  bool v = threadInterrupted(t, *thread);
  if (clear) {
    threadInterrupted(t, *thread) = false;
  }
  monitorRelease(t, local::interruptLock(t, *thread));

  return v;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsInterrupted)(Thread* t, jobject thread, jboolean clear)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(thread), clear };

  return run(t, jvmIsInterrupted, arguments);
}

uint64_t
jvmHoldsLock(Thread* t, uintptr_t* arguments)
{
  object m = objectMonitor(t, *reinterpret_cast<jobject>(arguments[0]), false);

  return m and monitorOwner(t, m) == t;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_HoldsLock)(Thread* t, jclass, jobject o)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(o) };

  return run(t, jvmHoldsLock, arguments);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_DumpAllStacks)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetAllThreads)(Thread*, jclass) { abort(); }

uint64_t
jvmDumpThreads(Thread* t, uintptr_t* arguments)
{
  jobjectArray threads = reinterpret_cast<jobjectArray>(arguments[0]);

  unsigned threadsLength = objectArrayLength(t, *threads);
  object arrayClass = resolveObjectArrayClass
    (t, classLoader(t, type(t, Machine::StackTraceElementType)),
     type(t, Machine::StackTraceElementType));
  object result = makeObjectArray(t, arrayClass, threadsLength);
  PROTECT(t, result);

  for (unsigned threadsIndex = 0; threadsIndex < threadsLength;
       ++ threadsIndex)
  {
    Thread* peer = reinterpret_cast<Thread*>
      (threadPeer(t, objectArrayBody(t, *threads, threadsIndex)));

    if (peer) {
      object trace = t->m->processor->getStackTrace(t, peer);
      PROTECT(t, trace);

      unsigned traceLength = objectArrayLength(t, trace);
      object array = makeObjectArray
        (t, type(t, Machine::StackTraceElementType), traceLength);
      PROTECT(t, array);

      for (unsigned traceIndex = 0; traceIndex < traceLength; ++ traceIndex) {
        object ste = makeStackTraceElement
          (t, objectArrayBody(t, trace, traceIndex));
        set(t, array, ArrayBody + (traceIndex * BytesPerWord), ste);
      }

      set(t, result, ArrayBody + (threadsIndex * BytesPerWord), array);
    }
  }

  return reinterpret_cast<uint64_t>(makeLocalReference(t, result));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_DumpThreads)(Thread* t, jclass, jobjectArray threads)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(threads) };

  return reinterpret_cast<jobjectArray>(run(t, jvmDumpThreads, arguments));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_CurrentLoadedClass)(Thread*) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_CurrentClassLoader)(Thread*) { abort(); }

uint64_t
jvmGetClassContext(Thread* t, uintptr_t*)
{
  object trace = getTrace(t, 1);
  PROTECT(t, trace);

  object context = makeObjectArray
    (t, type(t, Machine::JclassType), objectArrayLength(t, trace));
  PROTECT(t, context);

  for (unsigned i = 0; i < objectArrayLength(t, trace); ++i) {
    object c = getJClass
      (t, methodClass(t, traceElementMethod(t, objectArrayBody(t, trace, i))));

    set(t, context, ArrayBody + (i * BytesPerWord), c);
  }

  return reinterpret_cast<uint64_t>(makeLocalReference(t, context));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassContext)(Thread* t)
{
  return reinterpret_cast<jobjectArray>(run(t, jvmGetClassContext, 0));
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ClassDepth)(Thread*, jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ClassLoaderDepth)(Thread*) { abort(); }

uint64_t
jvmGetSystemPackage(Thread* t, uintptr_t* arguments)
{
  jstring s = reinterpret_cast<jstring>(arguments[0]);

  ACQUIRE(t, t->m->classLock);

  THREAD_RUNTIME_ARRAY(t, char, chars, stringLength(t, *s) + 1);
  stringChars(t, *s, RUNTIME_ARRAY_BODY(chars));

  object key = makeByteArray(t, RUNTIME_ARRAY_BODY(chars));

  object array = hashMapFind
    (t, root(t, Machine::PackageMap), key, byteArrayHash, byteArrayEqual);

  if (array) {
    return reinterpret_cast<uintptr_t>
      (makeLocalReference
       (t, t->m->classpath->makeString
        (t, array, 0, byteArrayLength(t, array))));
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_GetSystemPackage)(Thread* t, jstring s)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(s) };

  return reinterpret_cast<jstring>(run(t, jvmGetSystemPackage, arguments));
}

uint64_t
jvmGetSystemPackages(Thread* t, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>
    (makeLocalReference
     (t, makeObjectArray
      (t, resolveClass
       (t, root(t, Machine::BootLoader), "java/lang/Package"), 0)));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetSystemPackages)(Thread* t)
{
  return reinterpret_cast<jobjectArray>(run(t, jvmGetSystemPackages, 0));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_AllocateNewObject)(Thread*, jobject, jclass,
                      jclass) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_AllocateNewArray)(Thread*, jobject, jclass,
                     jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_LatestUserDefinedLoader)(Thread* t)
{
  ENTER(t, Thread::ActiveState);

  class Visitor: public Processor::StackVisitor {
   public:
    Visitor(Thread* t):
      t(t), loader(0)
    { }

    virtual bool visit(Processor::StackWalker* walker) {
      object loader = classLoader(t, methodClass(t, walker->method()));
      if (loader
          and loader != root(t, Machine::BootLoader)
          and strcmp
          (&byteArrayBody(t, className(t, objectClass(t, loader)), 0),
           reinterpret_cast<const int8_t*>
           ("sun/reflect/DelegatingClassLoader")))
      {
        this->loader = loader;
        return false;
      } else {
        return true;
      }
    }

    Thread* t;
    object loader;
  } v(t);

  t->m->processor->walkStack(t, &v);

  return makeLocalReference(t, v.loader);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_LoadClass0)(Thread*, jobject, jclass,
               jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetArrayLength)(Thread* t, jobject array)
{
  ENTER(t, Thread::ActiveState);

  return cast<uintptr_t>(*array, BytesPerWord);
}

uint64_t
jvmGetArrayElement(Thread* t, uintptr_t* arguments)
{
  jobject array = reinterpret_cast<jobject>(arguments[0]);
  jint index = arguments[1];

  switch (byteArrayBody(t, className(t, objectClass(t, *array)), 1)) {
  case 'Z':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeBoolean(t, cast<int8_t>(*array, ArrayBody + index))));
  case 'B':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeByte(t, cast<int8_t>(*array, ArrayBody + index))));
  case 'C':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeChar(t, cast<int16_t>(*array, ArrayBody + (index * 2)))));
  case 'S':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeShort(t, cast<int16_t>(*array, ArrayBody + (index * 2)))));
  case 'I':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeInt(t, cast<int32_t>(*array, ArrayBody + (index * 4)))));
  case 'F':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeFloat(t, cast<int32_t>(*array, ArrayBody + (index * 4)))));
  case 'J':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeLong(t, cast<int64_t>(*array, ArrayBody + (index * 8)))));
  case 'D':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, makeDouble(t, cast<int64_t>(*array, ArrayBody + (index * 8)))));
  case 'L':
  case '[':
    return reinterpret_cast<intptr_t>
      (makeLocalReference
       (t, cast<object>(*array, ArrayBody + (index * BytesPerWord))));
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetArrayElement)(Thread* t, jobject array, jint index)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(array),
                            static_cast<uintptr_t>(index) };

  return reinterpret_cast<jobject>(run(t, jvmGetArrayElement, arguments));
}

extern "C" JNIEXPORT jvalue JNICALL
EXPORT(JVM_GetPrimitiveArrayElement)(Thread*, jobject, jint, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetArrayElement)(Thread* t, jobject array, jint index,
                            jobject value)
{
  ENTER(t, Thread::ActiveState);

  switch (byteArrayBody(t, className(t, objectClass(t, *array)), 1)) {
  case 'Z':
    cast<int8_t>(*array, ArrayBody + index) = booleanValue(t, *value);
    break;
  case 'B':
    cast<int8_t>(*array, ArrayBody + index) = byteValue(t, *value);
    break;
  case 'C':
    cast<int16_t>(*array, ArrayBody + (index * 2)) = charValue(t, *value);
    break;
  case 'S':
    cast<int16_t>(*array, ArrayBody + (index * 2)) = shortValue(t, *value);
    break;
  case 'I':
    cast<int32_t>(*array, ArrayBody + (index * 4)) = intValue(t, *value);
    break;
  case 'F':
    cast<int32_t>(*array, ArrayBody + (index * 4)) = floatValue(t, *value);
    break;
  case 'J':
    cast<int64_t>(*array, ArrayBody + (index * 8)) = longValue(t, *value);
    break;
  case 'D':
    cast<int64_t>(*array, ArrayBody + (index * 8)) = doubleValue(t, *value);
    break;
  case 'L':
  case '[':
    set(t, *array, ArrayBody + (index * BytesPerWord), (value ? *value : 0));
    break;
  default:
    abort(t);
  }
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetPrimitiveArrayElement)(Thread*, jobject, jint, jvalue,
                             unsigned char) { abort(); }

object
makeNewArray(Thread* t, object c, unsigned length)
{
  if (classVmFlags(t, c) & PrimitiveFlag) {
    const char* name = reinterpret_cast<char*>
      (&byteArrayBody(t, local::getClassName(t, c), 0));

    switch (*name) {
    case 'b':
      if (name[1] == 'o') {
        return makeBooleanArray(t, length);
      } else {
        return makeByteArray(t, length);
      }
    case 'c': return makeCharArray(t, length);
    case 'd': return makeDoubleArray(t, length);
    case 'f': return makeFloatArray(t, length);
    case 'i': return makeIntArray(t, length);
    case 'l': return makeLongArray(t, length);
    case 's': return makeShortArray(t, length);
    default: abort(t);
    }
  } else {
    return makeObjectArray(t, c, length);
  }
}

uint64_t
jvmNewArray(Thread* t, uintptr_t* arguments)
{
  jclass elementClass = reinterpret_cast<jclass>(arguments[0]);
  jint length = arguments[1];

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, makeNewArray(t, jclassVmClass(t, *elementClass), length)));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_NewArray)(Thread* t, jclass elementClass, jint length)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(elementClass),
                            static_cast<uintptr_t>(length) };

  return reinterpret_cast<jobject>(run(t, jvmNewArray, arguments));
}

uint64_t
jvmNewMultiArray(Thread* t, uintptr_t* arguments)
{
  jclass elementClass = reinterpret_cast<jclass>(arguments[0]);
  jintArray dimensions = reinterpret_cast<jintArray>(arguments[1]);

  THREAD_RUNTIME_ARRAY(t, int32_t, counts, intArrayLength(t, *dimensions));
  for (int i = intArrayLength(t, *dimensions) - 1; i >= 0; --i) {
    RUNTIME_ARRAY_BODY(counts)[i] = intArrayBody(t, *dimensions, i);
    if (UNLIKELY(RUNTIME_ARRAY_BODY(counts)[i] < 0)) {
      throwNew(t, Machine::NegativeArraySizeExceptionType, "%d",
               RUNTIME_ARRAY_BODY(counts)[i]);
      return 0;
    }
  }

  object array = makeNewArray
    (t, jclassVmClass(t, *elementClass), RUNTIME_ARRAY_BODY(counts)[0]);
  PROTECT(t, array);

  populateMultiArray(t, array, RUNTIME_ARRAY_BODY(counts), 0,
                     intArrayLength(t, *dimensions));

  return reinterpret_cast<uint64_t>(makeLocalReference(t, array));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_NewMultiArray)(Thread* t, jclass elementClass,
                          jintArray dimensions)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(elementClass),
                            reinterpret_cast<uintptr_t>(dimensions) };

  return reinterpret_cast<jobject>(run(t, jvmNewMultiArray, arguments));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetCallerClass)(Thread* t, int target)
{
  ENTER(t, Thread::ActiveState);

  object method = getCaller(t, target, true);

  return method ? makeLocalReference
    (t, getJClass(t, methodClass(t, method))) : 0;
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindPrimitiveClass)(Thread* t, const char* name)
{
  ENTER(t, Thread::ActiveState);

  switch (*name) {
  case 'b':
    if (name[1] == 'o') {
      return makeLocalReference
        (t, getJClass(t, type(t, Machine::JbooleanType)));
    } else {
      return makeLocalReference
        (t, getJClass(t, type(t, Machine::JbyteType)));
    }
  case 'c':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JcharType)));
  case 'd':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JdoubleType)));
  case 'f':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JfloatType)));
  case 'i':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JintType)));
  case 'l':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JlongType)));
  case 's':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JshortType)));
  case 'v':
    return makeLocalReference
      (t, getJClass(t, type(t, Machine::JvoidType)));
  default:
    throwNew(t, Machine::IllegalArgumentExceptionType);
  }
}

uint64_t
jvmResolveClass(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/Classes", "link",
     "(Lavian/VMClass;)V");

  t->m->processor->invoke(t, method, 0, jclassVmClass(t, *c));

  return 1;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ResolveClass)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  run(t, jvmResolveClass, arguments);
}

uint64_t
jvmFindClassFromClassLoader(Thread* t, uintptr_t* arguments)
{
  const char* name = reinterpret_cast<const char*>(arguments[0]);
  jboolean init = arguments[1];
  jobject loader = reinterpret_cast<jobject>(arguments[2]);
  jboolean throwError = arguments[3];

  object c = resolveClass
    (t, loader ? *loader : root(t, Machine::BootLoader), name, true,
     throwError ? Machine::NoClassDefFoundErrorType
     : Machine::ClassNotFoundExceptionType);

  if (init) {
    PROTECT(t, c);

    initClass(t, c);
  }

  return reinterpret_cast<uint64_t>(makeLocalReference(t, getJClass(t, c)));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindClassFromClassLoader)(Thread* t, const char* name,
                                     jboolean init, jobject loader,
                                     jboolean throwError)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(name),
                            init,
                            reinterpret_cast<uintptr_t>(loader),
                            throwError };

  return reinterpret_cast<jclass>
    (run(t, jvmFindClassFromClassLoader, arguments));
}

extern "C" JNIEXPORT jclass JNICALL
JVM_FindClassFromBootLoader(Thread* t, const char* name)
{
  return EXPORT(JVM_FindClassFromClassLoader)(t, name, false, 0, false);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindClassFromClass)(Thread*, const char*, jboolean, jclass)
{ abort(); }

uint64_t
jvmFindLoadedClass(Thread* t, uintptr_t* arguments)
{
  jobject loader = reinterpret_cast<jobject>(arguments[0]);
  jstring name = reinterpret_cast<jstring>(arguments[1]);

  object spec = makeByteArray(t, stringLength(t, *name) + 1);

  { char* s = reinterpret_cast<char*>(&byteArrayBody(t, spec, 0));
    stringChars(t, *name, s);
    replace('.', '/', s);
  }

  object c = findLoadedClass(t, *loader, spec);
    
  return reinterpret_cast<uint64_t>
    (c ? makeLocalReference(t, getJClass(t, c)) : 0);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindLoadedClass)(Thread* t, jobject loader, jstring name)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(loader),
                            reinterpret_cast<uintptr_t>(name) };

  return reinterpret_cast<jclass>(run(t, jvmFindLoadedClass, arguments));
}

uint64_t
jvmDefineClass(Thread* t, uintptr_t* arguments)
{
  jobject loader = reinterpret_cast<jobject>(arguments[0]);
  const uint8_t* data = reinterpret_cast<const uint8_t*>(arguments[1]);
  jsize length = arguments[2];

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, getJClass(t, defineClass(t, *loader, data, length))));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_DefineClass)(Thread* t, const char*, jobject loader,
                        const uint8_t* data, jsize length, jobject)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(loader),
                            reinterpret_cast<uintptr_t>(data),
                            static_cast<uintptr_t>(length) };

  return reinterpret_cast<jclass>(run(t, jvmDefineClass, arguments));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_DefineClassWithSource)(Thread* t, const char*, jobject loader,
                          const uint8_t* data, jsize length, jobject,
                          const char*)
{
  return EXPORT(JVM_DefineClass)(t, 0, loader, data, length, 0);
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_GetClassName)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, jclassName(t, *c));
}

uint64_t
jvmGetClassInterfaces(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);

  object addendum = classAddendum(t, jclassVmClass(t, *c));
  if (addendum) {
    object table = classAddendumInterfaceTable(t, addendum);
    if (table) {
      PROTECT(t, table);

      object array = makeObjectArray(t, arrayLength(t, table));
      PROTECT(t, array);

      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        object c = getJClass(t, arrayBody(t, table, i));
        set(t, array, ArrayBody + (i * BytesPerWord), c);
      }

      return reinterpret_cast<uint64_t>(makeLocalReference(t, array));
    }
  }

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, makeObjectArray(t, type(t, Machine::JclassType), 0)));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassInterfaces)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jclass>(run(t, jvmGetClassInterfaces, arguments));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetClassLoader)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object loader = classLoader(t, jclassVmClass(t, *c));

  if (loader == root(t, Machine::BootLoader)) {
    // sun.misc.Unsafe.getUnsafe expects a null result if the class
    // loader is the boot classloader and will throw a
    // SecurityException otherwise.
    object caller = getCaller(t, 2);
    if (caller and strcmp
        (reinterpret_cast<const char*>
         (&byteArrayBody(t, className(t, methodClass(t, caller)), 0)),
         "sun/misc/Unsafe") == 0)
    {
      return 0;
    } else {
      return makeLocalReference(t, root(t, Machine::BootLoader));
    }
  } else {
    return makeLocalReference(t, loader);
  }
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsInterface)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return (classFlags(t, jclassVmClass(t, *c)) & ACC_INTERFACE) != 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassSigners)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object runtimeData = getClassRuntimeDataIfExists(t, jclassVmClass(t, *c));

  return runtimeData ? makeLocalReference
    (t, classRuntimeDataSigners(t, runtimeData)) : 0;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetClassSigners)(Thread* t, jclass c, jobjectArray signers)
{
  ENTER(t, Thread::ActiveState);

  object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

  set(t, runtimeData, ClassRuntimeDataSigners, *signers);
}

uint64_t
jvmGetProtectionDomain(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/OpenJDK", "getProtectionDomain",
     "(Lavian/VMClass;)Ljava/security/ProtectionDomain;");

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, t->m->processor->invoke(t, method, 0, jclassVmClass(t, *c))));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetProtectionDomain)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jobject>(run(t, jvmGetProtectionDomain, arguments));
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetProtectionDomain)(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsArrayClass)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return classArrayDimensions(t, jclassVmClass(t, *c)) != 0;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsPrimitiveClass)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return (classVmFlags(t, jclassVmClass(t, *c)) & PrimitiveFlag) != 0;
}

uint64_t
jvmGetComponentType(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jobject>(arguments[0]);

  if (classArrayDimensions(t, jclassVmClass(t, *c))) {
    uint8_t n = byteArrayBody(t, className(t, jclassVmClass(t, *c)), 1);
    if (n != 'L' and n != '[') {
      return reinterpret_cast<uintptr_t>
        (makeLocalReference(t, getJClass(t, primitiveClass(t, n))));
    } else {
      return reinterpret_cast<uintptr_t>
        (makeLocalReference
         (t, getJClass(t, classStaticTable(t, jclassVmClass(t, *c)))));
    }
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetComponentType)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jclass>(run(t, jvmGetComponentType, arguments));
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassModifiers)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return classFlags(t, jclassVmClass(t, *c));
}

uint64_t
jvmGetDeclaredClasses(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jobject>(arguments[0]);

  object addendum = classAddendum(t, jclassVmClass(t, *c));
  if (addendum) {
    object table = classAddendumInnerClassTable(t, addendum);
    if (table) {
      PROTECT(t, table);

      unsigned count = 0;
      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        if (innerClassReferenceOuter(t, arrayBody(t, table, i))) {
          ++ count;
        }
      }

      object result = makeObjectArray(t, count);
      PROTECT(t, result);

      for (unsigned i = 0; i < arrayLength(t, table); ++i) {
        if (innerClassReferenceOuter(t, arrayBody(t, table, i))) {
          object inner = getJClass
            (t, resolveClass
             (t, classLoader(t, jclassVmClass(t, *c)), referenceName
              (t, innerClassReferenceInner(t, arrayBody(t, table, i)))));
          
          -- count;
          set(t, result, ArrayBody + (count * BytesPerWord), inner);
        }
      }

      return reinterpret_cast<uintptr_t>(makeLocalReference(t, result));
    }
  }

  return reinterpret_cast<uintptr_t>
    (makeLocalReference(t, makeObjectArray(t, 0)));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetDeclaredClasses)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jclass>(run(t, jvmGetDeclaredClasses, arguments));  
}

uint64_t
jvmGetDeclaringClass(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jobject>(arguments[0]);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "avian/OpenJDK", "getDeclaringClass",
     "(Lavian/VMClass;)Ljava/lang/Class;");

  return reinterpret_cast<uintptr_t>
    (makeLocalReference
     (t, t->m->processor->invoke(t, method, 0, jclassVmClass(t, *c))));
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetDeclaringClass)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jclass>(run(t, jvmGetDeclaringClass, arguments));
}

uint64_t
jvmGetClassSignature(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jobject>(arguments[0]);

  object addendum = classAddendum(t, jclassVmClass(t, *c));
  if (addendum) {
    object signature = addendumSignature(t, addendum);
    if (signature) {
      return reinterpret_cast<uintptr_t>
        (makeLocalReference
         (t, t->m->classpath->makeString
          (t, signature, 0, byteArrayLength(t, signature) - 1)));
    }
  }
  return 0;
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_GetClassSignature)(Thread* t, jclass c)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c) };

  return reinterpret_cast<jclass>(run(t, jvmGetClassSignature, arguments));
}

extern "C" JNIEXPORT jbyteArray JNICALL
EXPORT(JVM_GetClassAnnotations)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object addendum = classAddendum(t, jclassVmClass(t, *c));
  return addendum
    ? makeLocalReference(t, addendumAnnotationTable(t, addendum)) : 0;
}

uint64_t
jvmGetClassDeclaredMethods(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  jboolean publicOnly = arguments[1];

  object table = getClassMethodTable(t, jclassVmClass(t, *c));
  if (table) {
    PROTECT(t, table);

    object array = makeObjectArray
      (t, type(t, Machine::JmethodType),
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
        PROTECT(t, parameterTypes);

        object returnType = local::resolveJType
          (t, classLoader(t, jclassVmClass(t, *c)), reinterpret_cast<char*>
           (&byteArrayBody(t, methodSpec(t, vmMethod), returnTypeSpec)),
           byteArrayLength(t, methodSpec(t, vmMethod)) - 1 - returnTypeSpec);
        PROTECT(t, returnType);

        object exceptionTypes = local::resolveExceptionJTypes
          (t, classLoader(t, jclassVmClass(t, *c)),
           methodAddendum(t, vmMethod));
        PROTECT(t, exceptionTypes);

        object signature;
        object annotationTable;
        object annotationDefault;
        object addendum = methodAddendum(t, vmMethod);
        if (addendum) {
          signature = addendumSignature(t, addendum);
          if (signature) {
            signature = t->m->classpath->makeString
              (t, signature, 0, byteArrayLength(t, signature) - 1);
          }

          annotationTable = addendumAnnotationTable(t, addendum);

          annotationDefault = methodAddendumAnnotationDefault(t, addendum);
        } else {
          signature = 0;
          annotationTable = 0;
          annotationDefault = 0;
        }

        if (annotationTable or annotationDefault) {
          PROTECT(t, signature);
          PROTECT(t, annotationTable);
          PROTECT(t, annotationDefault);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
              addendumPool(t, methodAddendum(t, vmMethod)));
        }

        object method = makeJmethod
          (t, true, 0, *c, i, name, returnType, parameterTypes, exceptionTypes,
           methodFlags(t, vmMethod), signature, 0, annotationTable, 0,
           annotationDefault, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), method);
      }
    }

    return reinterpret_cast<uint64_t>(makeLocalReference(t, array));
  } else {
    return reinterpret_cast<uint64_t>
      (makeLocalReference
       (t, makeObjectArray(t, type(t, Machine::JmethodType), 0)));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredMethods)(Thread* t, jclass c, jboolean publicOnly)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c), publicOnly };

  return reinterpret_cast<jobjectArray>
    (run(t, jvmGetClassDeclaredMethods, arguments));
}

uint64_t
jvmGetClassDeclaredFields(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  jboolean publicOnly = arguments[1];
  object table = classFieldTable(t, jclassVmClass(t, *c));
  if (table) {
    PROTECT(t, table);

    object array = makeObjectArray
      (t, type(t, Machine::JfieldType),
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
        PROTECT(t, type);

        type = getJClass(t, type);

        object signature;
        object annotationTable;
        object addendum = fieldAddendum(t, vmField);
        if (addendum) {
          signature = addendumSignature(t, addendum);
          if (signature) {
            signature = t->m->classpath->makeString
              (t, signature, 0, byteArrayLength(t, signature) - 1);
          }

          annotationTable = addendumAnnotationTable(t, addendum);
        } else {
          signature = 0;
          annotationTable = 0;
        }

        if (annotationTable) {
          PROTECT(t, signature);
          PROTECT(t, annotationTable);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
              addendumPool(t, fieldAddendum(t, vmField)));
        }

        object field = makeJfield
          (t, true, 0, *c, i, name, type, fieldFlags
           (t, vmField), signature, 0, annotationTable, 0, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), field);
      }
    }
    assert(t, ai == objectArrayLength(t, array));

    return reinterpret_cast<uint64_t>(makeLocalReference(t, array));
  } else {
    return reinterpret_cast<uint64_t>
      (makeLocalReference
       (t, makeObjectArray(t, type(t, Machine::JfieldType), 0)));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredFields)(Thread* t, jclass c, jboolean publicOnly)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c), publicOnly };

  return reinterpret_cast<jobjectArray>
    (run(t, jvmGetClassDeclaredFields, arguments));
}

uint64_t
jvmGetClassDeclaredConstructors(Thread* t, uintptr_t* arguments)
{
  jclass c = reinterpret_cast<jclass>(arguments[0]);
  jboolean publicOnly = arguments[1];

  object table = getClassMethodTable(t, jclassVmClass(t, *c));
  if (table) {
    PROTECT(t, table);

    object array = makeObjectArray
      (t, type(t, Machine::JconstructorType),
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
        PROTECT(t, parameterTypes);

        object exceptionTypes = local::resolveExceptionJTypes
          (t, classLoader(t, jclassVmClass(t, *c)),
           methodAddendum(t, vmMethod));
        PROTECT(t, exceptionTypes);

        object signature;
        object annotationTable;
        object addendum = methodAddendum(t, vmMethod);
        if (addendum) {
          signature = addendumSignature(t, addendum);
          if (signature) {
            signature = t->m->classpath->makeString
              (t, signature, 0, byteArrayLength(t, signature) - 1);
          }

          annotationTable = addendumAnnotationTable(t, addendum);
        } else {
          signature = 0;
          annotationTable = 0;
        }

        if (annotationTable) {
          PROTECT(t, signature);
          PROTECT(t, annotationTable);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
              addendumPool(t, methodAddendum(t, vmMethod)));
        }

        object method = makeJconstructor
          (t, true, 0, *c, i, parameterTypes, exceptionTypes, methodFlags
           (t, vmMethod), signature, 0, annotationTable, 0, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), method);
      }
    }

    return reinterpret_cast<uint64_t>(makeLocalReference(t, array));
  } else {
    return reinterpret_cast<uint64_t>
      (makeLocalReference
       (t, makeObjectArray(t, type(t, Machine::JconstructorType), 0)));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredConstructors)(Thread* t, jclass c,
                                         jboolean publicOnly)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(c), publicOnly };

  return reinterpret_cast<jobjectArray>
    (run(t, jvmGetClassDeclaredConstructors, arguments));
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassAccessFlags)(Thread* t, jclass c)
{
  return EXPORT(JVM_GetClassModifiers)(t, c);
}

uint64_t
jvmInvokeMethod(Thread* t, uintptr_t* arguments)
{
  jobject method = reinterpret_cast<jobject>(arguments[0]);
  jobject instance = reinterpret_cast<jobject>(arguments[1]);
  jobjectArray args = reinterpret_cast<jobjectArray>(arguments[2]);

  object vmMethod = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, jmethodClazz(t, *method))),
      jmethodSlot(t, *method));

  if (methodFlags(t, vmMethod) & ACC_STATIC) {
    instance = 0;
  }

  unsigned returnCode = methodReturnCode(t, vmMethod);

  THREAD_RESOURCE0(t, {
      if (t->exception) {
        object exception = t->exception;
        t->exception = makeThrowable
          (t, Machine::InvocationTargetExceptionType, 0, 0, exception);
      }
    });

  object result;
  if (args) {
    result = t->m->processor->invokeArray
      (t, vmMethod, instance ? *instance : 0, *args);
  } else {
    result = t->m->processor->invoke(t, vmMethod, instance ? *instance : 0);
  }

  return reinterpret_cast<uint64_t>
    (makeLocalReference(t, translateInvokeResult(t, returnCode, result)));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InvokeMethod)(Thread* t, jobject method, jobject instance,
                         jobjectArray args)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(method),
                            reinterpret_cast<uintptr_t>(instance),
                            reinterpret_cast<uintptr_t>(args) };

  return reinterpret_cast<jobject>(run(t, jvmInvokeMethod, arguments));
}

uint64_t
jvmNewInstanceFromConstructor(Thread* t, uintptr_t* arguments)
{
  jobject constructor = reinterpret_cast<jobject>(arguments[0]);
  jobjectArray args = reinterpret_cast<jobjectArray>(arguments[1]);

  object instance = make
    (t, jclassVmClass(t, jconstructorClazz(t, *constructor)));
  PROTECT(t, instance);

  object method = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, jconstructorClazz(t, *constructor))),
      jconstructorSlot(t, *constructor));

  THREAD_RESOURCE0(t, {
      if (t->exception) {
        object exception = t->exception;
        t->exception = makeThrowable
          (t, Machine::InvocationTargetExceptionType, 0, 0, exception);
      }
    });

  if (args) {
    t->m->processor->invokeArray(t, method, instance, *args);
  } else {
    t->m->processor->invoke(t, method, instance);
  }

  return reinterpret_cast<uint64_t>(makeLocalReference(t, instance));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_NewInstanceFromConstructor)(Thread* t, jobject constructor,
                                       jobjectArray args)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(constructor),
                            reinterpret_cast<uintptr_t>(args) };

  return reinterpret_cast<jobject>
    (run(t, jvmNewInstanceFromConstructor, arguments));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetClassConstantPool)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object vmClass = jclassVmClass(t, *c);
  object addendum = classAddendum(t, vmClass);
  object pool;
  if (addendum) {
    pool = addendumPool(t, addendum);
  } else {
    pool = 0;
  }

  if (pool == 0) {
    pool = classRuntimeDataPool(t, getClassRuntimeData(t, vmClass));
  }

  return makeLocalReference(t, makeConstantPool(t, pool));
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ConstantPoolGetSize)(Thread* t, jobject, jobject pool)
{
  if (pool == 0) return 0;

  ENTER(t, Thread::ActiveState);

  return singletonCount(t, *pool);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_ConstantPoolGetClassAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_ConstantPoolGetClassAtIfLoaded)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_ConstantPoolGetMethodAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_ConstantPoolGetMethodAtIfLoaded)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_ConstantPoolGetFieldAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_ConstantPoolGetFieldAtIfLoaded)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_ConstantPoolGetMemberRefInfoAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ConstantPoolGetIntAt)(Thread* t, jobject, jobject pool, jint index)
{
  ENTER(t, Thread::ActiveState);

  return singletonValue(t, *pool, index - 1);
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_ConstantPoolGetLongAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jfloat JNICALL
EXPORT(JVM_ConstantPoolGetFloatAt)(Thread*, jobject, jobject, jint)
{ abort(); }

uint64_t
jvmConstantPoolGetDoubleAt(Thread* t, uintptr_t* arguments)
{
  jobject pool = reinterpret_cast<jobject>(arguments[0]);
  jint index = arguments[1];

  double v; memcpy(&v, &singletonValue(t, *pool, index - 1), 8);

  return doubleToBits(v);
}

extern "C" JNIEXPORT jdouble JNICALL
EXPORT(JVM_ConstantPoolGetDoubleAt)(Thread* t, jobject, jobject pool,
                                    jint index)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(pool),
                            static_cast<uintptr_t>(index) };

  return bitsToDouble
    (run(t, jvmConstantPoolGetDoubleAt, arguments));
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_ConstantPoolGetStringAt)(Thread*, jobject, jobject, jint)
{ abort(); }

uint64_t
jvmConstantPoolGetUTF8At(Thread* t, uintptr_t* arguments)
{
  jobject pool = reinterpret_cast<jobject>(arguments[0]);
  jint index = arguments[1];

  object array = singletonObject(t, *pool, index - 1);

  return reinterpret_cast<uint64_t>
    (makeLocalReference
     (t, t->m->classpath->makeString
      (t, array, 0, cast<uintptr_t>(array, BytesPerWord) - 1)));
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_ConstantPoolGetUTF8At)(Thread* t, jobject, jobject pool, jint index)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(pool),
                            static_cast<uintptr_t>(index) };

  return reinterpret_cast<jstring>
    (run(t, jvmConstantPoolGetUTF8At, arguments));
}

void
maybeWrap(Thread* t, bool wrapException)
{
  if (t->exception
      and wrapException
      and not (instanceOf(t, type(t, Machine::ErrorType), t->exception)
               or instanceOf
               (t, type(t, Machine::RuntimeExceptionType), t->exception)))
  {
    object exception = t->exception;
    t->exception = 0;

    PROTECT(t, exception);

    object paeClass = resolveClass
      (t, root(t, Machine::BootLoader),
       "java/security/PrivilegedActionException");
    PROTECT(t, paeClass);

    object paeConstructor = resolveMethod
      (t, paeClass, "<init>", "(Ljava/lang/Exception;)V");
    PROTECT(t, paeConstructor);

    object result = make(t, paeClass);
    PROTECT(t, result);
    
    t->m->processor->invoke(t, paeConstructor, result, exception);

    t->exception = result;
  }
}

uint64_t
jvmDoPrivileged(Thread* t, uintptr_t* arguments)
{
  jobject action = reinterpret_cast<jobject>(arguments[0]);
  jboolean wrapException = arguments[1];

  // todo: cache these class and method lookups in the t->m->classpath
  // object:

  object privilegedAction = resolveClass
    (t, root(t, Machine::BootLoader), "java/security/PrivilegedAction");

  object method;
  if (instanceOf(t, privilegedAction, *action)) {
    method = resolveMethod
      (t, privilegedAction, "run", "()Ljava/lang/Object;");
  } else {
    object privilegedExceptionAction = resolveClass
      (t, root(t, Machine::BootLoader),
       "java/security/PrivilegedExceptionAction");

    method = resolveMethod
      (t, privilegedExceptionAction, "run", "()Ljava/lang/Object;");
  }

  THREAD_RESOURCE(t, jboolean, wrapException, maybeWrap(t, wrapException));

  return reinterpret_cast<uint64_t>
    (makeLocalReference(t, t->m->processor->invoke(t, method, *action)));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_DoPrivileged)
(Thread* t, jclass, jobject action, jobject, jboolean wrapException)
{
  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(action),
                            wrapException };

  return reinterpret_cast<jobject>(run(t, jvmDoPrivileged, arguments));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetInheritedAccessControlContext)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetStackAccessControlContext)(Thread*, jclass)
{
  return 0;
}

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_RegisterSignal)(jint, void*) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_RaiseSignal)(jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_FindSignal)(const char*)
{
  return -1;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_DesiredAssertionStatus)(Thread*, jclass, jclass)
{
  return false;
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_AssertionStatusDirectives)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_SupportsCX8)()
{
  return true;
}

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetClassNameUTF)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetClassCPTypes)(Thread*, jclass, unsigned char*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassCPEntriesCount)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassFieldsCount)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassMethodsCount)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetMethodIxExceptionIndexes)(Thread*, jclass, jint,
                                unsigned short*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxExceptionsCount)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetMethodIxByteCode)(Thread*, jclass, jint,
                        unsigned char*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxByteCodeLength)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetMethodIxExceptionTableEntry)(Thread*, jclass, jint,
                                   jint,
                                   local::JVM_ExceptionTableEntryType*)
{ abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxExceptionTableLength)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetFieldIxModifiers)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxModifiers)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxLocalsCount)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxArgsSize)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetMethodIxMaxStack)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsConstructorIx)(Thread*, jclass, int) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetMethodIxNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetMethodIxSignatureUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPFieldNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPMethodNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPMethodSignatureUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPFieldSignatureUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPClassNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPFieldClassNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT const char* JNICALL
EXPORT(JVM_GetCPMethodClassNameUTF)(Thread*, jclass, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetCPFieldModifiers)(Thread*, jclass, int, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetCPMethodModifiers)(Thread*, jclass, int, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ReleaseUTF)(const char*) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsSameClassPackage)(Thread*, jclass, jclass) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetLastErrorString)(char* dst, int length)
{
  strncpy(dst, strerror(errno), length);
  return strlen(dst);
}

extern "C" JNIEXPORT char* JNICALL
EXPORT(JVM_NativePath)(char* path)
{
  return path;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Open)(const char* path, jint flags, jint mode)
{
  int r = OPEN(path, flags, mode);
  if (r == -1) {
    return errno == EEXIST ? JVM_EEXIST : -1;
  } else {
    return r;
  }
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Close)(jint fd)
{
  return CLOSE(fd);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Read)(jint fd, char* dst, jint length)
{
  return READ(fd, dst, length);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Write)(jint fd, char* src, jint length)
{
  return WRITE(fd, src, length);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Available)(jint fd, jlong* result)
{
  struct STAT buffer;
  int n;
  if (FSTAT(fd, &buffer) >= 0
      and (S_ISCHR(buffer.st_mode)
           or S_ISFIFO(buffer.st_mode)
           or S_ISSOCK(buffer.st_mode))
      and local::pipeAvailable(fd, &n))
  {
    *result = n;
    return 1;
  }

  int current = LSEEK(fd, 0, SEEK_CUR);
  if (current == -1) return 0;

  int end = LSEEK(fd, 0, SEEK_END);
  if (end == -1) return 0;

  if (LSEEK(fd, current, SEEK_SET) == -1) return 0;

  *result = end - current;
  return 1;
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_Lseek)(jint fd, jlong offset, jint seek)
{
  return LSEEK(fd, offset, seek);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SetLength)(jint fd, jlong length)
{
#ifdef PLATFORM_WINDOWS
  HANDLE h = reinterpret_cast<HANDLE>(_get_osfhandle(fd));
  if (h == INVALID_HANDLE_VALUE) {
    errno = EBADF;
    return -1;
  }

  long high = length >> 32;
  DWORD r = SetFilePointer(h, static_cast<long>(length), &high, FILE_BEGIN);
  if (r == 0xFFFFFFFF and GetLastError() != NO_ERROR) {
    errno = EIO;
    return -1;
  }

  if (SetEndOfFile(h)) {
    return 0;
  } else {
    errno = EIO;
    return -1;
  }
#else
  return ftruncate(fd, length);
#endif
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Sync)(jint fd)
{
#ifdef PLATFORM_WINDOWS
  HANDLE h = reinterpret_cast<HANDLE>(_get_osfhandle(fd));
  if (h == INVALID_HANDLE_VALUE) {
    errno = EBADF;
    return -1;
  }

  if (FlushFileBuffers(h)) {
    return 0;
  } else {
    errno = EIO;
    return -1;
  }
#else
  return fsync(fd);
#endif
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_InitializeSocketLibrary)()
{
#ifdef PLATFORM_WINDOWS
  static bool wsaInitialized = false;
  if (not wsaInitialized) {
    WSADATA data;
    int r = WSAStartup(MAKEWORD(2, 2), &data);
    if (r or LOBYTE(data.wVersion) != 2 or HIBYTE(data.wVersion) != 2) {
      return -1;
    } else {
      wsaInitialized = true;
    }
  }
#endif
  return 0;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Socket)(jint domain, jint type, jint protocol)
{
  return socket(domain, type, protocol);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SocketClose)(jint socket)
{
#ifdef PLATFORM_WINDOWS
  return closesocket(socket);
#else
  return close(socket);
#endif
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SocketShutdown)(jint socket, jint how)
{
  return shutdown(socket, how);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Recv)(jint socket, char* dst, jint count, jint flags)
{
  return recv(socket, dst, count, flags);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Send)(jint socket, char* src, jint count, jint flags)
{
  return send(socket, src, count, flags);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Timeout)(int, long) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Listen)(jint socket, jint count)
{
  return listen(socket, count);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Connect)(jint socket, sockaddr* address, jint addressLength)
{
  return connect(socket, address, addressLength);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Bind)(jint, struct sockaddr*, jint) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_Accept)(jint socket, struct sockaddr* address, jint* addressLength)
{
  socklen_t length = *addressLength;
  int r = accept(socket, address, &length);
  *addressLength = length;
  return r;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_RecvFrom)(jint, char*, int,
             int, struct sockaddr*, int*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SendTo)(jint, char*, int,
           int, struct sockaddr*, int) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SocketAvailable)(jint socket, jint* count)
{
#ifdef PLATFORM_WINDOWS
  unsigned long c = *count;
  int r = ioctlsocket(socket, FIONREAD, &c);
  *count = c;
  return r;
#else
  return ioctl(socket, FIONREAD, count) < 0 ? 0 : 1;
#endif
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetSockName)(jint socket, struct sockaddr* address,
                        int* addressLength)
{
  socklen_t length = *addressLength;
  int r = getsockname(socket, address, &length);
  *addressLength = length;
  return r;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetSockOpt)(jint socket, int level, int optionName,
                       char* optionValue, int* optionLength)
{
  socklen_t length = *optionLength;
  int rv = getsockopt(socket, level, optionName, optionValue, &length);
  *optionLength = length;
  return rv;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SetSockOpt)(jint socket, int level, int optionName,
                       const char* optionValue, int optionLength)
{
  return setsockopt(socket, level, optionName, optionValue, optionLength);
}

extern "C" JNIEXPORT struct protoent* JNICALL
EXPORT(JVM_GetProtoByName)(char*) { abort(); }

extern "C" JNIEXPORT struct hostent* JNICALL
EXPORT(JVM_GetHostByAddr)(const char*, int, int) { abort(); }

extern "C" JNIEXPORT struct hostent* JNICALL
EXPORT(JVM_GetHostByName)(char*) { abort(); }

extern "C" JNIEXPORT int JNICALL
EXPORT(JVM_GetHostName)(char* name, int length)
{
  return gethostname(name, length);
}

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_RawMonitorCreate)(void)
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
EXPORT(JVM_RawMonitorDestroy)(void* lock)
{
  static_cast<System::Monitor*>(lock)->dispose();
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_RawMonitorEnter)(void* lock)
{
  static_cast<System::Monitor*>(lock)->acquire
    (static_cast<Thread*>
     (local::globalMachine->localThread->get())->systemThread);

  return 0;
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_RawMonitorExit)(void* lock)
{
  static_cast<System::Monitor*>(lock)->release
    (static_cast<Thread*>
     (local::globalMachine->localThread->get())->systemThread);
}

int JNICALL
GetVersion(Thread*)
{
  return JMM_VERSION_1_0;
}

uint64_t
getInputArgumentArray(Thread* t, uintptr_t*)
{
  object array = makeObjectArray
    (t, type(t, Machine::StringType), t->m->argumentCount);
  PROTECT(t, array);

  for (unsigned i = 0; i < t->m->argumentCount; ++i) {
    object argument = makeString(t, t->m->arguments[i]);
    set(t, array, ArrayBody + (i * BytesPerWord), argument);
  }

  return reinterpret_cast<uintptr_t>(makeLocalReference(t, array));
}

jobjectArray JNICALL
GetInputArgumentArray(Thread* t)
{
  return reinterpret_cast<jobjectArray>(run(t, getInputArgumentArray, 0));
}

jint JNICALL  
GetOptionalSupport(Thread*, jmmOptionalSupport* support)
{
  memset(support, 0, sizeof(jmmOptionalSupport));
  return 0;
}

jlong JNICALL
GetLongAttribute(Thread* t, jobject, jmmLongAttribute attribute)
{
  const unsigned JMM_JVM_INIT_DONE_TIME_MS = 7;

  switch (attribute) {
  case JMM_JVM_INIT_DONE_TIME_MS:
    return 0;

  default:
    abort(t);
  }
}

jboolean JNICALL
GetBoolAttribute(Thread* t, jmmBoolAttribute attribute)
{
  const unsigned JMM_THREAD_CPU_TIME = 24;
  const unsigned JMM_THREAD_ALLOCATED_MEMORY = 25;

  switch (attribute) {
  case JMM_THREAD_CPU_TIME:
  case JMM_THREAD_ALLOCATED_MEMORY:
    return false;

  default:
    abort(t);
  }
}

uint64_t
getMemoryManagers(Thread* t, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>
    (makeLocalReference
     (t, makeObjectArray
      (t, resolveClass
       (t, root(t, Machine::BootLoader),
        "java/lang/management/MemoryManagerMXBean"), 0)));
}

jobjectArray JNICALL
GetMemoryManagers(Thread* t, jobject)
{
  return reinterpret_cast<jobjectArray>(run(t, getMemoryManagers, 0));
}

uint64_t
getMemoryPools(Thread* t, uintptr_t*)
{
  return reinterpret_cast<uintptr_t>
    (makeLocalReference
     (t, makeObjectArray
      (t, resolveClass
       (t, root(t, Machine::BootLoader),
        "java/lang/management/MemoryPoolMXBean"), 0)));
}

jobjectArray JNICALL
GetMemoryPools(Thread* t, jobject)
{
  return reinterpret_cast<jobjectArray>(run(t, getMemoryPools, 0));
}

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_GetManagement)(jint version)
{
  if (version == JMM_VERSION_1_0) {
    JmmInterface* interface
      = &(static_cast<MyClasspath*>
          (local::globalMachine->classpath)->jmmInterface);

    memset(interface, 0, sizeof(JmmInterface));

    interface->GetVersion = GetVersion;
    interface->GetOptionalSupport = GetOptionalSupport;
    interface->GetLongAttribute = GetLongAttribute;
    interface->GetBoolAttribute = GetBoolAttribute;
    interface->GetMemoryManagers = GetMemoryManagers;
    interface->GetMemoryPools = GetMemoryPools;
    interface->GetInputArgumentArray = GetInputArgumentArray;

    return interface; 
  } else {
    return 0;
  }
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InitAgentProperties)(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetEnclosingMethodInfo)(JNIEnv*, jclass)
{
  // todo: implement properly
  return 0;
}

extern "C" JNIEXPORT jintArray JNICALL
EXPORT(JVM_GetThreadStateValues)(JNIEnv*, jint) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetThreadStateNames)(JNIEnv*, jint, jintArray) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetVersionInfo)(JNIEnv*, local::jvm_version_info* info, size_t size)
{
  memset(info, 0, size);
  info->jvm_version = 0x01070000;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CX8Field)(JNIEnv*, jobject*, jfieldID*, jlong, jlong)
{ abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetNativeThreadName)(JNIEnv*, jobject, jstring) { abort(); }

} // namespace local

} // namespace

extern "C" JNIEXPORT int
jio_vsnprintf(char* dst, size_t size, const char* format, va_list a)
{
  return vm::vsnprintf(dst, size, format, a);
}

extern "C" JNIEXPORT int
jio_vfprintf(FILE* stream, const char* format, va_list a)
{
  return vfprintf(stream, format, a);
}

#ifdef PLATFORM_WINDOWS
extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_GetThreadInterruptEvent)()
{
  // hack: We don't want to expose thread interruption implementation
  // details, so we give the class library a fake event to play with.
  // This means that threads won't be interruptable when blocked in
  // Process.waitFor.
  static HANDLE fake = 0;
  if (fake == 0) {
    fake = CreateEvent(0, true, false, 0);
  }
  return fake;
}

namespace { HMODULE jvmHandle = 0; }

extern "C" int JDK_InitJvmHandle()
{
  jvmHandle = GetModuleHandle(0);
  return jvmHandle != 0;
}
 
extern "C" void* JDK_FindJvmEntry(const char* name)
{
  return voidPointer(GetProcAddress(jvmHandle, name));
}

#  ifdef AVIAN_OPENJDK_SRC

extern "C" char* findJavaTZ_md(const char*, const char*);

extern "C" JNIEXPORT int64_t JNICALL
Avian_java_util_TimeZone_getSystemTimeZoneID
(Thread* t, object, uintptr_t* arguments)
{
  // On Windows, findJavaTZ_md loads tzmappings from the filesystem
  // using fopen, so we have no opportunity to make it read straight
  // from the embedded JAR file as with files read from Java code.
  // Therefore, we must extract tzmappings to a temporary location
  // before calling findJavaTZ_md.  We could avoid this by
  // implementing findJavaTZ_md ourselves from scratch, but that would
  // be a lot of code to implement and maintain.

  object country = reinterpret_cast<object>(arguments[1]);

  THREAD_RUNTIME_ARRAY(t, char, countryChars, stringLength(t, country) + 1);
  stringChars(t, country, RUNTIME_ARRAY_BODY(countryChars));

  local::MyClasspath* cp = static_cast<local::MyClasspath*>(t->m->classpath);

  local::EmbeddedFile ef(cp, cp->tzMappings, cp->tzMappingsLength);
  if (ef.jar == 0 or ef.jarLength == 0 or ef.pathLength == 0) {
    return 0;
  }

  Finder* finder = local::getFinder(t, ef.jar, ef.jarLength);
  if (finder == 0) {
    return 0;
  }

  System::Region* r = finder->find(ef.path);
  if (r == 0) {
    return 0;
  }

  THREAD_RESOURCE(t, System::Region*, r, r->dispose());

  char tmpPath[MAX_PATH + 1];
  GetTempPathA(MAX_PATH, tmpPath);

  char tmpDir[MAX_PATH + 1];
  vm::snprintf(tmpDir, MAX_PATH, "%s/avian-tmp", tmpPath);
  if (_mkdir(tmpDir) != 0 and errno != EEXIST) {
    return 0;
  }

  THREAD_RESOURCE(t, char*, tmpDir, rmdir(tmpDir)); 

  char libDir[MAX_PATH + 1];
  vm::snprintf(libDir, MAX_PATH, "%s/lib", tmpDir);
  if (mkdir(libDir) != 0 and errno != EEXIST) {
    return 0;
  }

  THREAD_RESOURCE(t, char*, libDir, rmdir(libDir)); 

  char file[MAX_PATH + 1];
  vm::snprintf(file, MAX_PATH, "%s/tzmappings", libDir);
  FILE* out = vm::fopen(file, "wb");
  if (out == 0) {
    return 0;
  }
    
  THREAD_RESOURCE(t, char*, file, unlink(file)); 
  THREAD_RESOURCE(t, FILE*, out, fclose(out));

  if (fwrite(r->start(), 1, r->length(), out) != r->length()
      or fflush(out) != 0)
  {
    return 0;
  }

  char* javaTZ = findJavaTZ_md(tmpDir, RUNTIME_ARRAY_BODY(countryChars));
  if (javaTZ) {
    THREAD_RESOURCE(t, char*, javaTZ, free(javaTZ));

    return reinterpret_cast<int64_t>(makeString(t, "%s", javaTZ));
  } else {
    return 0;
  }
}
#  else // not AVIAN_OPENJDK_SRC
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
jio_fprintf(FILE* stream, const char* format, ...)
{
  va_list a;
  va_start(a, format);

  int r = jio_vfprintf(stream, format, a);

  va_end(a);

  return r;
}
#  endif // not AVIAN_OPENJDK_SRC
#endif // PLATFORM_WINDOWS
