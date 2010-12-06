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
#    define CREAT _creat
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
  RUNTIME_ARRAY(char, s, byteArrayLength(t, name));
  replace('/', '.', RUNTIME_ARRAY_BODY(s),
          reinterpret_cast<char*>(&byteArrayBody(t, name, 0)));

  return makeString(t, "%s", s);
}

void
interceptFileOperations(Thread*);

class MyClasspath : public Classpath {
 public:
  static const unsigned BufferSize = 1024;

  MyClasspath(System* s, Allocator* allocator, const char* javaHome,
              const char* embedPrefix):
    allocator(allocator)
  {
    class StringBuilder {
     public:
      StringBuilder(System* s, char* pointer, unsigned remaining):
        s(s), pointer(pointer), remaining(remaining)
      { }

      void append(const char* append) {
        unsigned length = strlen(append);
        expect(s, remaining > length);
  
        strncpy(pointer, append, remaining);
        
        remaining -= length;
        pointer += length;
      }

      void append(char c) {
        assert(s, remaining > 1);
        
        pointer[0] = c;
        pointer[1] = 0;

        -- remaining;
        ++ pointer;
      }

      System* s;
      char* pointer;
      unsigned remaining;
    } sb(s, buffer, BufferSize);

    this->javaHome = sb.pointer;
    sb.append(javaHome);
    sb.append('\0');

    this->classpath = sb.pointer;
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
    sb.append("/lib/resources.jar");
    sb.append('\0');

    this->libraryPath = sb.pointer;
    sb.append(javaHome);
#ifdef PLATFORM_WINDOWS
    sb.append("/bin");
#elif defined ARCH_x86_64
    sb.append("/lib/amd64");
#else
    // todo: handle other architectures
    sb.append("/lib/i386");
#endif
    sb.append('\0');
    
    this->zipLibrary = sb.pointer;
    sb.append(this->libraryPath);
    sb.append("/");
    sb.append(SO_PREFIX);
    sb.append("zip");
    sb.append(SO_SUFFIX);
    sb.append('\0');
    
    this->netLibrary = sb.pointer;
    sb.append(this->libraryPath);
    sb.append("/");
    sb.append(SO_PREFIX);
    sb.append("net");
    sb.append(SO_SUFFIX);
    sb.append('\0');

    this->nioLibrary = sb.pointer;
    sb.append(this->libraryPath);
    sb.append("/");
    sb.append(SO_PREFIX);
    sb.append("nio");
    sb.append(SO_SUFFIX);
    sb.append('\0');
    
    this->tzMappings = sb.pointer;
    sb.append(javaHome);
    sb.append("/lib/tzmappings");
    this->tzMappingsLength = sb.pointer - tzMappings;
    sb.append('\0');

    this->embedPrefix = sb.pointer;
    sb.append(embedPrefix);
    this->embedPrefixLength = sb.pointer - this->embedPrefix;
  }

  virtual object
  makeJclass(Thread* t, object class_)
  {
    PROTECT(t, class_);

    object name = makeClassNameString(t, getClassName(t, class_));

    return vm::makeJclass
      (t, 0, 0, name, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, class_);
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
    threadContextClassLoader(t, thread) = root(t, Machine::BootLoader);

    PROTECT(t, thread);

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
  runThread(Thread* t)
  {
    object method = resolveMethod
      (t, root(t, Machine::BootLoader), "java/lang/Thread", "run", "()V");

    if (LIKELY(t->exception == 0)) {
      t->m->processor->invoke(t, method, t->javaThread);
    }

    acquire(t, t->javaThread);
    t->flags &= ~Thread::ActiveFlag;
    notifyAll(t, t->javaThread);
    release(t, t->javaThread);
  }

  virtual object
  makeThrowable
  (Thread* t, Machine::Type type, object message, object trace, object cause)
  {
    PROTECT(t, message);
    PROTECT(t, trace);
    PROTECT(t, cause);
    
    if (trace == 0) {
      trace = makeTrace(t);
    }

    object result = make(t, vm::type(t, type));
    
    set(t, result, ThrowableMessage, message);
    set(t, result, ThrowableTrace, trace);
    set(t, result, ThrowableCause, cause);

    return result;
  }

  virtual void
  boot(Thread* t)
  {
    globalMachine = t->m;

    resolveSystemClass(t, root(t, Machine::BootLoader),
                       className(t, type(t, Machine::ClassLoaderType)));
    if (UNLIKELY(t->exception)) return;

#ifdef AVIAN_OPENJDK_SRC
    interceptFileOperations(t);
    if (UNLIKELY(t->exception)) return;
#else // not AVIAN_OPENJDK_SRC
    if (loadLibrary(t, libraryPath, "verify", true, true) == 0
        or loadLibrary(t, libraryPath, "java", true, true) == 0)
    {
      abort(t);
    }
#endif // not AVIAN_OPENJDK_SRC

    object constructor = resolveMethod
      (t, type(t, Machine::ClassLoaderType), "<init>",
       "(Ljava/lang/ClassLoader;)V");
    if (UNLIKELY(t->exception)) return;
    PROTECT(t, constructor);

    t->m->processor->invoke(t, constructor, root(t, Machine::BootLoader), 0);
    if (UNLIKELY(t->exception)) return;

    t->m->processor->invoke
      (t, constructor, root(t, Machine::AppLoader),
       root(t, Machine::BootLoader));
    if (UNLIKELY(t->exception)) return;

    object scl = resolveField
      (t, type(t, Machine::ClassLoaderType), "scl", "Ljava/lang/ClassLoader;");
    if (UNLIKELY(t->exception)) return;
    PROTECT(t, scl);

    object sclSet = resolveField
      (t, type(t, Machine::ClassLoaderType), "sclSet", "Z");
    if (UNLIKELY(t->exception)) return;

    set(t, classStaticTable(t, type(t, Machine::ClassLoaderType)),
        fieldOffset(t, scl), root(t, Machine::AppLoader));

    cast<uint8_t>(classStaticTable(t, type(t, Machine::ClassLoaderType)),
                  fieldOffset(t, sclSet)) = true;

    t->m->processor->invoke
      (t, root(t, Machine::BootLoader), "java/lang/System",
       "initializeSystemClass", "()V", 0);
  }

  virtual const char*
  bootClasspath()
  {
    return classpath;
  }

  virtual void
  dispose()
  { 
    allocator->free(this, sizeof(*this));
  }

  Allocator* allocator;
  const char* javaHome;
  const char* classpath;
  const char* libraryPath;
  const char* zipLibrary;
  const char* netLibrary;
  const char* nioLibrary;
  const char* tzMappings;
  const char* embedPrefix;
  unsigned tzMappingsLength;
  unsigned embedPrefixLength;
  unsigned filePathField;
  unsigned fileDescriptorFdField;
  unsigned fileInputStreamFdField;
  char buffer[BufferSize];
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

  RUNTIME_ARRAY(char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  if (pathEqual(cp->zipLibrary, RUNTIME_ARRAY_BODY(p))
      or pathEqual(cp->netLibrary, RUNTIME_ARRAY_BODY(p))
      or pathEqual(cp->nioLibrary, RUNTIME_ARRAY_BODY(p)))
  {
    return Exists | Regular;
  } else {
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
      object r = t->m->processor->invoke
        (t, nativeInterceptOriginal
         (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
         reinterpret_cast<object>(arguments[0]), file);
      
      return (r ? intValue(t, r) : 0);
    }
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

  RUNTIME_ARRAY(char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  if (pathEqual(cp->zipLibrary, RUNTIME_ARRAY_BODY(p))
      or pathEqual(cp->netLibrary, RUNTIME_ARRAY_BODY(p))
      or pathEqual(cp->nioLibrary, RUNTIME_ARRAY_BODY(p)))
  {
    return mask == Read;
  } else {
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
      object r = t->m->processor->invoke
        (t, nativeInterceptOriginal
         (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
         reinterpret_cast<object>(arguments[0]), file, mask);
      
      return (r ? booleanValue(t, r) : false);
    }
  }
}

int64_t JNICALL
getFileLength
(Thread* t, object method, uintptr_t* arguments)
{
  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  object file = reinterpret_cast<object>(arguments[1]);
  object path = cast<object>(file, cp->filePathField);

  RUNTIME_ARRAY(char, p, stringLength(t, path) + 1);
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
    object r = t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       reinterpret_cast<object>(arguments[0]), file);

    return (r ? longValue(t, r) : 0);
  }
}

void JNICALL
openFile(Thread* t, object method, uintptr_t* arguments)
{
  object this_ = reinterpret_cast<object>(arguments[0]);
  object path = reinterpret_cast<object>(arguments[1]);

  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  RUNTIME_ARRAY(char, p, stringLength(t, path) + 1);
  stringChars(t, path, RUNTIME_ARRAY_BODY(p));
  replace('\\', '/', RUNTIME_ARRAY_BODY(p));

  EmbeddedFile ef(cp, RUNTIME_ARRAY_BODY(p), stringLength(t, path));
  if (ef.jar) {
    if (ef.jarLength == 0 or ef.pathLength == 0) {
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::FileNotFoundExceptionType);
      return;
    }

    Finder* finder = getFinder(t, ef.jar, ef.jarLength);
    if (finder == 0) {
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::FileNotFoundExceptionType);
      return;
    }

    System::Region* r = finder->find(ef.path);
    if (r == 0) {
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::FileNotFoundExceptionType);
      return;
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
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::IoExceptionType);
      return 0;
    }
  } else {
    object r = t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_);

    return r ? intValue(t, r) : 0;
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
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::IoExceptionType);
      return 0;
    }
  } else {
    object r = t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_, dst, offset, length);

    return r ? intValue(t, r) : 0;
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
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::IoExceptionType);
      return 0;
    }
  } else {
    object r = t->m->processor->invoke
      (t, nativeInterceptOriginal
       (t, methodRuntimeDataNative(t, getMethodRuntimeData(t, method))),
       this_, count);

    return r ? longValue(t, r) : 0;
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
      t->exception = t->m->classpath->makeThrowable
        (t, Machine::IoExceptionType);
      return 0;
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
  }
}

void
interceptFileOperations(Thread* t)
{
  MyClasspath* cp = static_cast<MyClasspath*>(t->m->classpath);

  { object fileClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/File");
    if (fileClass == 0) return;

    object filePathField = findFieldInClass2
      (t, fileClass, "path", "Ljava/lang/String;");
    if (filePathField == 0) return;

    cp->filePathField = fieldOffset(t, filePathField);
  }

  { object fileDescriptorClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/FileDescriptor");
    if (fileDescriptorClass == 0) return;

    object fileDescriptorFdField = findFieldInClass2
      (t, fileDescriptorClass, "fd", "I");
    if (fileDescriptorFdField == 0) return;

    cp->fileDescriptorFdField = fieldOffset(t, fileDescriptorFdField);
  }

  { object fileInputStreamClass = resolveClass
      (t, root(t, Machine::BootLoader), "java/io/FileInputStream");
    if (fileInputStreamClass == 0) return;

    object fileInputStreamFdField = findFieldInClass2
      (t, fileInputStreamClass, "fd", "Ljava/io/FileDescriptor;");
    if (fileInputStreamFdField == 0) return;

    cp->fileInputStreamFdField = fieldOffset(t, fileInputStreamFdField);

    PROTECT(t, fileInputStreamClass);

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
    if (fsClass == 0) return;

    PROTECT(t, fsClass);

    intercept(t, fsClass, gbaMethodName, "(Ljava/io/File;)I",
              voidPointer(getFileAttributes));

    intercept(t, fsClass, "checkAccess", "(Ljava/io/File;I)Z",
              voidPointer(checkFileAccess));
  
    intercept(t, fsClass, "getLength", "(Ljava/io/File;)J",
              voidPointer(getFileLength));
  }

  intercept(t, type(t, Machine::ClassLoaderType), "getBootstrapResource",
            "(Ljava/lang/String;)Ljava/net/URL;",
            voidPointer(getBootstrapResource));

  intercept(t, type(t, Machine::ClassLoaderType), "getBootstrapResources",
            "(Ljava/lang/String;)Ljava/util/Enumeration;",
            voidPointer(getBootstrapResources));
}

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

object
interruptLock(Thread* t, object thread)
{
  if (threadInterruptLock(t, thread) == 0) {
    PROTECT(t, thread);
    ACQUIRE(t, t->m->referenceLock);

    if (threadInterruptLock(t, thread) == 0) {
      object head = makeMonitorNode(t, 0, 0);
      object lock = makeMonitor(t, 0, 0, 0, head, head, 0);
      set(t, thread, ThreadInterruptLock, lock);
    }
  }
  
  return threadInterruptLock(t, thread);
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

  uint8_t* buffer = static_cast<uint8_t*>
    (t->m->heap->allocate(length));
  memcpy(buffer, &byteArrayBody(t, data, offset), length);
  object c = defineClass(t, loader, buffer, length);

  return c ? reinterpret_cast<int64_t>(getJClass(t, c)) : 0;
}

extern "C" JNIEXPORT int64_t
Avian_sun_misc_Unsafe_allocateInstance
(Thread* t, object, uintptr_t* arguments)
{
  object c = jclassVmClass(t, reinterpret_cast<object>(arguments[1]));
  PROTECT(t, c);

  initClass(t, c);
  if (UNLIKELY(t->exception)) return 0;

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
Avian_sun_misc_Unsafe_arrayBaseOffset
(Thread*, object, uintptr_t*)
{
  return BytesPerWord * 2;
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
Avian_sun_misc_Unsafe_getInt__Ljava_lang_Object_2J
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

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putInt__Ljava_lang_Object_2JI
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  int32_t value = arguments[4];

  cast<int32_t>(o, offset) = value;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getBoolean
(Thread*, object, uintptr_t* arguments)
{
  object o = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);

  return cast<uint8_t>(o, offset);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putBoolean
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
(Thread*, object, uintptr_t* arguments)
{
  object target = reinterpret_cast<object>(arguments[1]);
  int64_t offset; memcpy(&offset, arguments + 2, 8);
  uint64_t expect; memcpy(&expect, arguments + 4, 8);
  uint64_t update; memcpy(&update, arguments + 6, 8);

  return atomicCompareAndSwap64
    (&cast<uint64_t>(target, offset), expect, update);
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
Avian_sun_misc_Unsafe_setMemory
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int64_t count; memcpy(&count, arguments + 3, 8);
  int8_t v = arguments[5];

  memset(reinterpret_cast<int8_t*>(p), v, count);
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putLong__JJ
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int64_t v; memcpy(&v, arguments + 3, 8);

  *reinterpret_cast<int64_t*>(p) = v;
}

extern "C" JNIEXPORT void JNICALL
Avian_sun_misc_Unsafe_putInt__JI
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);
  int32_t v = arguments[3];

  *reinterpret_cast<int32_t*>(p) = v;
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getByte__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int8_t*>(p);
}

extern "C" JNIEXPORT int64_t JNICALL
Avian_sun_misc_Unsafe_getInt__J
(Thread*, object, uintptr_t* arguments)
{
  int64_t p; memcpy(&p, arguments + 1, 8);

  return *reinterpret_cast<int32_t*>(p);
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
  
  if (absolute) {
    time -= t->m->system->now();
    if (time <= 0) {
      return;
    }
  }

  monitorAcquire(t, local::interruptLock(t, t->javaThread));
  while (not (threadUnparked(t, t->javaThread)
              or monitorWait(t, local::interruptLock(t, t->javaThread), time)))
  { }
  threadUnparked(t, t->javaThread) = false;
  monitorRelease(t, local::interruptLock(t, t->javaThread));
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetInterfaceVersion)()
{
  return local::InterfaceVersion;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_IHashCode)(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return objectHash(t, *o);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorWait)(Thread* t, jobject o, jlong milliseconds)
{
  ENTER(t, Thread::ActiveState);

  vm::wait(t, *o, milliseconds);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorNotify)(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  notify(t, *o);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_MonitorNotifyAll)(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  notifyAll(t, *o);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_Clone)(Thread* t, jobject o)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, clone(t, *o));
}

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_InternString)(Thread* t, jstring s)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, intern(t, *s));
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

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ArrayCopy)(Thread* t, jclass, jobject src, jint srcOffset,
                      jobject dst, jint dstOffset, jint length)
{
  ENTER(t, Thread::ActiveState);

  arrayCopy(t, *src, srcOffset, *dst, dstOffset, length);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InitProperties)(Thread* t, jobject properties)
{
  ENTER(t, Thread::ActiveState);

  object method = resolveMethod
    (t, root(t, Machine::BootLoader), "java/util/Properties", "setProperty",
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
  local::setProperty(t, method, *properties, "user.home", getenv("HOME"));
  local::setProperty(t, method, *properties, "user.dir", getenv("PWD"));
#endif

  local::setProperty(t, method, *properties, "java.protocol.handler.pkgs",
                     "avian");

  local::setProperty
    (t, method, *properties, "java.home",
     static_cast<local::MyClasspath*>(t->m->classpath)->javaHome);

  local::setProperty
    (t, method, *properties, "sun.boot.library.path",
     static_cast<local::MyClasspath*>(t->m->classpath)->libraryPath);

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

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GC)()
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());
  
  ENTER(t, Thread::ActiveState);

  collect(t, Heap::MajorCollection);  
}

extern "C" JNIEXPORT jlong JNICALL
EXPORT(JVM_MaxObjectInspectionAge)(void) { abort(); }

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
  return 0;
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ActiveProcessorCount)()
{
  return 1;
}

extern "C" JNIEXPORT jint JNICALL
net_JNI_OnLoad(JavaVM*, void*);

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_LoadLibrary)(const char* path)
{
  Thread* t = static_cast<Thread*>(local::globalMachine->localThread->get());

  RUNTIME_ARRAY(char, p, strlen(path) + 1);
  replace('\\', '/', RUNTIME_ARRAY_BODY(p), path);

#ifdef AVIAN_OPENJDK_SRC
  if (local::pathEqual
      (static_cast<local::MyClasspath*>(t->m->classpath)->zipLibrary,
       RUNTIME_ARRAY_BODY(p))
      or local::pathEqual
      (static_cast<local::MyClasspath*>(t->m->classpath)->nioLibrary,
       RUNTIME_ARRAY_BODY(p)))
  {
    return t->m->libraries;
  } else if (local::pathEqual
             (static_cast<local::MyClasspath*>(t->m->classpath)->netLibrary,
              RUNTIME_ARRAY_BODY(p)))
  {
    net_JNI_OnLoad(t->m, 0);
    return t->m->libraries;
  }
#endif // AVIAN_OPENJDK_SRC

  ENTER(t, Thread::ActiveState);

  return loadLibrary
    (t, static_cast<local::MyClasspath*>(t->m->classpath)->libraryPath,
     RUNTIME_ARRAY_BODY(p), false, false);
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
  return version <= JNI_VERSION_1_4;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsNaN)(jdouble) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_FillInStackTrace)(Thread* t, jobject throwable)
{
  ENTER(t, Thread::ActiveState);

  object trace = getTrace(t, 1);
  set(t, *throwable, ThrowableTrace, trace);
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_PrintStackTrace)(Thread*, jobject, jobject) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetStackTraceDepth)(Thread* t, jobject throwable)
{
  ENTER(t, Thread::ActiveState);

  return objectArrayLength(t, throwableTrace(t, *throwable));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetStackTraceElement)(Thread* t, jobject throwable, jint index)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, makeStackTraceElement
     (t, objectArrayBody(t, throwableTrace(t, *throwable), index)));
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_InitializeCompiler) (Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsSilentCompiler)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CompileClass)(Thread*, jclass, jclass) { abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CompileClasses)(Thread*, jclass, jstring) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_CompilerCommand)(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_EnableCompiler)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_DisableCompiler)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_StartThread)(Thread* t, jobject thread)
{
  ENTER(t, Thread::ActiveState);

  startThread(t, *thread);
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
EXPORT(JVM_Yield)(Thread*, jclass)
{
#ifdef PLATFORM_WINDOWS
  SwitchToThread();
#else
  sched_yield();
#endif
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Sleep)(Thread* t, jclass, jlong milliseconds)
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
EXPORT(JVM_CurrentThread)(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, t->javaThread);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_CountStackFrames)(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_Interrupt)(Thread* t, jobject thread)
{
  ENTER(t, Thread::ActiveState);

  monitorAcquire(t, local::interruptLock(t, *thread));
  Thread* p = reinterpret_cast<Thread*>(threadPeer(t, *thread));
  if (p) {
    interrupt(t, p);
  } else {
    threadInterrupted(t, *thread) = true;
  }
  monitorRelease(t, local::interruptLock(t, *thread));
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_IsInterrupted)(Thread* t, jobject thread, jboolean clear)
{
  ENTER(t, Thread::ActiveState);

  monitorAcquire(t, local::interruptLock(t, *thread));
  bool v = threadInterrupted(t, *thread);
  if (clear) {
    threadInterrupted(t, *thread) = false;
  }
  monitorRelease(t, local::interruptLock(t, *thread));

  return v;
}

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_HoldsLock)(Thread*, jclass, jobject) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_DumpAllStacks)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetAllThreads)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_DumpThreads)(Thread* t, jclass, jobjectArray threads)
{
  ENTER(t, Thread::ActiveState);

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

  return makeLocalReference(t, result);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_CurrentLoadedClass)(Thread*) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_CurrentClassLoader)(Thread*) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassContext)(Thread* t)
{
  ENTER(t, Thread::ActiveState);

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

  return makeLocalReference(t, context);
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ClassDepth)(Thread*, jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_ClassLoaderDepth)(Thread*) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_GetSystemPackage)(Thread*, jstring)
{
  return 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetSystemPackages)(Thread*) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_AllocateNewObject)(Thread*, jobject, jclass,
                      jclass) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_AllocateNewArray)(Thread*, jobject, jclass,
                     jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_LatestUserDefinedLoader)(Thread*) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_LoadClass0)(Thread*, jobject, jclass,
               jstring) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetArrayLength)(Thread* t, jobject array)
{
  ENTER(t, Thread::ActiveState);

  return cast<uintptr_t>(*array, BytesPerWord);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetArrayElement)(Thread* t, jobject array, jint index)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference(t, objectArrayBody(t, *array, index));
}

extern "C" JNIEXPORT jvalue JNICALL
EXPORT(JVM_GetPrimitiveArrayElement)(Thread*, jobject, jint, jint) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetArrayElement)(Thread* t, jobject array, jint index,
                            jobject value)
{
  ENTER(t, Thread::ActiveState);

  set(t, *array, ArrayBody + (index * BytesPerWord), (value ? *value : 0));
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_SetPrimitiveArrayElement)(Thread*, jobject, jint, jvalue,
                             unsigned char) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_NewArray)(Thread* t, jclass elementClass, jint length)
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
    return makeLocalReference(t, makeObjectArray(t, c, length));
  }
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_NewMultiArray)(Thread*, jclass, jintArray) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetCallerClass)(Thread* t, int target)
{
  ENTER(t, Thread::ActiveState);

  return makeLocalReference
    (t, getJClass(t, methodClass(t, getCaller(t, target))));
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
    t->exception = t->m->classpath->makeThrowable
      (t, Machine::IllegalArgumentExceptionType);
    return 0;
  }
}

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_ResolveClass)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindClassFromClassLoader)(Thread* t, const char* name,
                                     jboolean init, jobject loader,
                                     jboolean throwError)
{
  ENTER(t, Thread::ActiveState);

  object c = resolveClass
    (t, loader ? *loader : root(t, Machine::BootLoader), name);
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
EXPORT(JVM_FindClassFromBootLoader)(Thread* t, const char* name,
                                    jboolean throwError)
{
  return EXPORT(JVM_FindClassFromClassLoader)(t, name, false, 0, throwError);
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindClassFromClass)(Thread*, const char*, jboolean,
                       jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_FindLoadedClass)(Thread* t, jobject loader, jstring name)
{
  ENTER(t, Thread::ActiveState);

  object spec = makeByteArray(t, stringLength(t, *name) + 1);

  { char* s = reinterpret_cast<char*>(&byteArrayBody(t, spec, 0));
    stringChars(t, *name, s);
    replace('.', '/', s);
  }

  object c = findLoadedClass(t, *loader, spec);
    
  return c ? makeLocalReference(t, getJClass(t, c)) : 0;
}

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_DefineClass)(Thread* t, const char*, jobject loader,
                        const uint8_t* data, jsize length, jobject)
{
  ENTER(t, Thread::ActiveState);

  object c = defineClass(t, *loader, data, length);

  return c ? makeLocalReference(t, getJClass(t, c)) : 0;
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

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassInterfaces)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object table = classInterfaceTable(t, jclassVmClass(t, *c));
  if (table) {
    unsigned stride =
      (classFlags(t, jclassVmClass(t, *c)) & ACC_INTERFACE) == 0 ? 2 : 1;

    object array = makeObjectArray
       (t, type(t, Machine::JclassType), arrayLength(t, table) / stride);
    PROTECT(t, array);

    for (unsigned i = 0; i < objectArrayLength(t, array); ++i) {
      object interface = getJClass(t, arrayBody(t, table, i * stride));
      set(t, array, ArrayBody + (i * BytesPerWord), interface);
    }

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray(t, type(t, Machine::JclassType), 0));
  }
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

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_GetProtectionDomain)(Thread* t, jclass)
{
  ENTER(t, Thread::ActiveState);

  object openJDK = resolveClass
    (t, root(t, Machine::BootLoader), "avian/OpenJDK");
  if (UNLIKELY(t->exception)) return 0;

  object method = resolveMethod
    (t, openJDK, "getProtectionDomain", "()Ljava/security/ProtectionDomain;");
  if (UNLIKELY(t->exception)) return 0;

  return makeLocalReference(t, t->m->processor->invoke(t, method, 0));
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

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetComponentType)(Thread* t, jclass c)
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
EXPORT(JVM_GetClassModifiers)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  return classFlags(t, jclassVmClass(t, *c));
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetDeclaredClasses)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jclass JNICALL
EXPORT(JVM_GetDeclaringClass)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_GetClassSignature)(Thread*, jclass) { abort(); }

extern "C" JNIEXPORT jbyteArray JNICALL
EXPORT(JVM_GetClassAnnotations)(Thread* t, jclass c)
{
  ENTER(t, Thread::ActiveState);

  object addendum = classAddendum(t, jclassVmClass(t, *c));
  return addendum
    ? makeLocalReference(t, addendumAnnotationTable(t, addendum)) : 0;
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredMethods)(Thread* t, jclass c, jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

  object table = classMethodTable(t, jclassVmClass(t, *c));
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
          PROTECT(t, signature);
          PROTECT(t, annotationTable);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
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
      (t, makeObjectArray(t, type(t, Machine::JmethodType), 0));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredFields)(Thread* t, jclass c, jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

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

        if (UNLIKELY(t->exception)) {
          return 0;
        }

        PROTECT(t, type);

        type = getJClass(t, type);

        object signature = t->m->classpath->makeString
          (t, fieldSpec(t, vmField), 0, byteArrayLength
           (t, fieldSpec(t, vmField)) - 1);

        object annotationTable = fieldAddendum(t, vmField) == 0
          ? 0 : addendumAnnotationTable(t, fieldAddendum(t, vmField));

        if (annotationTable) {
          PROTECT(t, signature);
          PROTECT(t, annotationTable);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
              addendumPool(t, fieldAddendum(t, vmField)));
        }

        object field = makeJfield
          (t, true, *c, i, name, type, fieldFlags
           (t, vmField), signature, 0, annotationTable, 0, 0, 0, 0, 0, 0);

        assert(t, ai < objectArrayLength(t, array));

        set(t, array, ArrayBody + ((ai++) * BytesPerWord), field);
      }
    }
    assert(t, ai == objectArrayLength(t, array));

    return makeLocalReference(t, array);
  } else {
    return makeLocalReference
      (t, makeObjectArray(t, type(t, Machine::JfieldType), 0));
  }
}

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetClassDeclaredConstructors)(Thread* t, jclass c,
                                         jboolean publicOnly)
{
  ENTER(t, Thread::ActiveState);

  object table = classMethodTable(t, jclassVmClass(t, *c));
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
          PROTECT(t, signature);
          PROTECT(t, annotationTable);

          object runtimeData = getClassRuntimeData(t, jclassVmClass(t, *c));

          set(t, runtimeData, ClassRuntimeDataPool,
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
      (t, makeObjectArray(t, type(t, Machine::JconstructorType), 0));
  }
}

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_GetClassAccessFlags)(Thread* t, jclass c)
{
  return EXPORT(JVM_GetClassModifiers)(t, c);
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InvokeMethod)(Thread* t, jobject method, jobject instance,
                 jobjectArray arguments)
{
  ENTER(t, Thread::ActiveState);

  object vmMethod = arrayBody
    (t, classMethodTable
     (t, jclassVmClass(t, jmethodClazz(t, *method))),
      jmethodSlot(t, *method));

  if (methodFlags(t, vmMethod) & ACC_STATIC) {
    instance = 0;
  }

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
EXPORT(JVM_NewInstanceFromConstructor)(Thread* t, jobject constructor,
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

extern "C" JNIEXPORT jdouble JNICALL
EXPORT(JVM_ConstantPoolGetDoubleAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_ConstantPoolGetStringAt)(Thread*, jobject, jobject, jint)
{ abort(); }

extern "C" JNIEXPORT jstring JNICALL
EXPORT(JVM_ConstantPoolGetUTF8At)(Thread* t, jobject, jobject pool, jint index)
{
  ENTER(t, Thread::ActiveState);

  object array = singletonObject(t, *pool, index - 1);

  return makeLocalReference
    (t, t->m->classpath->makeString
     (t, array, 0, cast<uintptr_t>(array, BytesPerWord) - 1));
}

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_DoPrivileged)
(Thread* t, jclass, jobject action, jobject, jboolean wrapException)
{
  ENTER(t, Thread::ActiveState);

  // todo: cache these class and method lookups in the t->m->classpath
  // object:

  object privilegedAction = resolveClass
    (t, root(t, Machine::BootLoader), "java/security/PrivilegedAction");
  
  if (UNLIKELY(t->exception)) {
    return 0;
  }

  object method;
  if (instanceOf(t, privilegedAction, *action)) {
    method = resolveMethod
      (t, privilegedAction, "run", "()Ljava/lang/Object;");
  } else {
    object privilegedExceptionAction = resolveClass
      (t, root(t, Machine::BootLoader),
       "java/security/PrivilegedExceptionAction");
    
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
          (instanceOf(t, type(t, Machine::ErrorType), t->exception)
           or instanceOf(t, type(t, Machine::RuntimeExceptionType),
            t->exception)))
      {
        object cause = t->exception;
        PROTECT(t, cause);

        t->exception = 0;

        object paeClass = resolveClass
          (t, root(t, Machine::BootLoader),
           "java/security/PrivilegedActionException");

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
  return OPEN(path, flags, mode);
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
EXPORT(JVM_SetLength)(jint, jlong) { abort(); }

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
EXPORT(JVM_SocketAvailable)(jint, jint*) { abort(); }

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
EXPORT(JVM_GetSockOpt)(jint, int, int, char*, int*) { abort(); }

extern "C" JNIEXPORT jint JNICALL
EXPORT(JVM_SetSockOpt)(jint, int, int, const char*, int) { abort(); }

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

extern "C" JNIEXPORT void* JNICALL
EXPORT(JVM_GetManagement)(jint) { abort(); }

extern "C" JNIEXPORT jobject JNICALL
EXPORT(JVM_InitAgentProperties)(Thread*, jobject) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetEnclosingMethodInfo)(JNIEnv*, jclass) { abort(); }

extern "C" JNIEXPORT jintArray JNICALL
EXPORT(JVM_GetThreadStateValues)(JNIEnv*, jint) { abort(); }

extern "C" JNIEXPORT jobjectArray JNICALL
EXPORT(JVM_GetThreadStateNames)(JNIEnv*, jint, jintArray) { abort(); }

extern "C" JNIEXPORT void JNICALL
EXPORT(JVM_GetVersionInfo)(JNIEnv*, local::jvm_version_info*, size_t)
{ abort(); }

extern "C" JNIEXPORT jboolean JNICALL
EXPORT(JVM_CX8Field)(JNIEnv*, jobject*, jfieldID*, jlong, jlong)
{ abort(); }

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
{ abort(); }

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

  RUNTIME_ARRAY(char, countryChars, stringLength(t, country) + 1);
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

  RESOURCE(System::Region*, r, r->dispose());

  char tmpPath[MAX_PATH + 1];
  GetTempPathA(MAX_PATH, tmpPath);

  char tmpDir[MAX_PATH + 1];
  vm::snprintf(tmpDir, MAX_PATH, "%s/avian-tmp", tmpPath);
  if (_mkdir(tmpDir) != 0 and errno != EEXIST) {
    return 0;
  }

  RESOURCE(char*, tmpDir, rmdir(tmpDir)); 

  char libDir[MAX_PATH + 1];
  vm::snprintf(libDir, MAX_PATH, "%s/lib", tmpDir);
  if (mkdir(libDir) != 0 and errno != EEXIST) {
    return 0;
  }

  RESOURCE(char*, libDir, rmdir(libDir)); 

  char file[MAX_PATH + 1];
  vm::snprintf(file, MAX_PATH, "%s/tzmappings", libDir);
  FILE* out = vm::fopen(file, "wb");
  if (out == 0) {
    return 0;
  }
    
  RESOURCE(char*, file, unlink(file)); 
  RESOURCE(FILE*, out, fclose(out));

  if (fwrite(r->start(), 1, r->length(), out) != r->length()
      or fflush(out) != 0)
  {
    return 0;
  }

  char* javaTZ = findJavaTZ_md(tmpDir, RUNTIME_ARRAY_BODY(countryChars));
  if (javaTZ) {
    object result = makeString(t, "%s", javaTZ);
    free(javaTZ);
    return reinterpret_cast<int64_t>(result);
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
