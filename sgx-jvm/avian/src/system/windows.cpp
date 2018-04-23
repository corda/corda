/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "sys/stat.h"
#include "windows.h"

#ifdef _MSC_VER
#define S_ISREG(x) ((x)&_S_IFREG)
#define S_ISDIR(x) ((x)&_S_IFDIR)
#define FTIME _ftime_s
#else
#define FTIME _ftime
#endif

#undef max
#undef min

#include "avian/arch.h"
#include <avian/system/system.h>
#include <avian/system/signal.h>
#include <avian/util/runtime-array.h>
#include <avian/append.h>

#if defined(WINAPI_FAMILY)

#if !WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)

#define WaitForSingleObject(hHandle, dwMilliseconds) \
  WaitForSingleObjectEx((hHandle), (dwMilliseconds), FALSE)

#define CreateEvent(lpEventAttributes, bManualReset, bInitialState, lpName) \
  CreateEventEx((lpEventAttributes),                                        \
                (lpName),                                                   \
                ((bManualReset) ? CREATE_EVENT_MANUAL_RESET : 0)            \
                | ((bInitialState) ? CREATE_EVENT_INITIAL_SET : 0),         \
                EVENT_ALL_ACCESS)

#define CreateMutex(lpEventAttributes, bInitialOwner, lpName)     \
  CreateMutexEx((lpEventAttributes),                              \
                (lpName),                                         \
                (bInitialOwner) ? CREATE_MUTEX_INITIAL_OWNER : 0, \
                MUTEX_ALL_ACCESS)

#include "thread-emulation.h"

#endif

#if defined(WINAPI_PARTITION_PHONE) \
    && WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_PHONE)
// Headers in Windows Phone 8 DevKit contain severe error, so let's define
// needed functions on our own
extern "C" {
WINBASEAPI
_Ret_maybenull_ HANDLE WINAPI
    CreateFileMappingFromApp(_In_ HANDLE hFile,
                             _In_opt_ PSECURITY_ATTRIBUTES SecurityAttributes,
                             _In_ ULONG PageProtection,
                             _In_ ULONG64 MaximumSize,
                             _In_opt_ PCWSTR Name);

WINBASEAPI
_Ret_maybenull_ __out_data_source(FILE) PVOID WINAPI
    MapViewOfFileFromApp(_In_ HANDLE hFileMappingObject,
                         _In_ ULONG DesiredAccess,
                         _In_ ULONG64 FileOffset,
                         _In_ SIZE_T NumberOfBytesToMap);

WINBASEAPI
BOOL WINAPI UnmapViewOfFile(_In_ LPCVOID lpBaseAddress);
}
#endif  // WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_PHONE)

#else

#ifndef WINAPI_PARTITION_DESKTOP
#define WINAPI_PARTITION_DESKTOP 1
#endif

#ifndef WINAPI_FAMILY_PARTITION
#define WINAPI_FAMILY_PARTITION(x) (x)
#endif

#endif

#define ACQUIRE(s, x) MutexResource MAKE_NAME(mutexResource_)(s, x)

using namespace vm;

namespace {

class MutexResource {
 public:
  MutexResource(System* s, HANDLE m) : s(s), m(m)
  {
    int r UNUSED = WaitForSingleObject(m, INFINITE);
    assertT(s, r == WAIT_OBJECT_0);
  }

  ~MutexResource()
  {
    bool success UNUSED = ReleaseMutex(m);
    assertT(s, success);
  }

 private:
  System* s;
  HANDLE m;
};

class MySystem;
MySystem* globalSystem;

DWORD WINAPI run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
  return 0;
}

const bool Verbose = false;

const unsigned Waiting = 1 << 0;
const unsigned Notified = 1 << 1;

class MySystem : public System {
 public:
  class Thread : public System::Thread {
   public:
    Thread(System* s, System::Runnable* r) : s(s), r(r), next(0), flags(0)
    {
      mutex = CreateMutex(0, false, 0);
      assertT(s, mutex);

      event = CreateEvent(0, true, false, 0);
      assertT(s, event);
    }

    virtual void interrupt()
    {
      ACQUIRE(s, mutex);

      r->setInterrupted(true);

      if (flags & Waiting) {
        int r UNUSED = SetEvent(event);
        assertT(s, r != 0);
      }
    }

    virtual bool getAndClearInterrupted()
    {
      ACQUIRE(s, mutex);

      bool interrupted = r->interrupted();

      r->setInterrupted(false);

      return interrupted;
    }

    virtual void join()
    {
      int r UNUSED = WaitForSingleObject(thread, INFINITE);
      assertT(s, r == WAIT_OBJECT_0);
    }

    virtual void dispose()
    {
      CloseHandle(event);
      CloseHandle(mutex);
      CloseHandle(thread);
      ::free(this);
    }

    HANDLE thread;
    HANDLE mutex;
    HANDLE event;
    System* s;
    System::Runnable* r;
    Thread* next;
    unsigned flags;
  };

  class Mutex : public System::Mutex {
   public:
    Mutex(System* s) : s(s)
    {
      mutex = CreateMutex(0, false, 0);
      assertT(s, mutex);
    }

    virtual void acquire()
    {
      int r UNUSED = WaitForSingleObject(mutex, INFINITE);
      assertT(s, r == WAIT_OBJECT_0);
    }

    virtual void release()
    {
      bool success UNUSED = ReleaseMutex(mutex);
      assertT(s, success);
    }

    virtual void dispose()
    {
      CloseHandle(mutex);
      ::free(this);
    }

    System* s;
    HANDLE mutex;
  };

  class Monitor : public System::Monitor {
   public:
    Monitor(System* s) : s(s), owner_(0), first(0), last(0), depth(0)
    {
      mutex = CreateMutex(0, false, 0);
      assertT(s, mutex);
    }

    virtual bool tryAcquire(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ == t) {
        ++depth;
        return true;
      } else {
        switch (WaitForSingleObject(mutex, 0)) {
        case WAIT_TIMEOUT:
          return false;

        case WAIT_OBJECT_0:
          owner_ = t;
          ++depth;
          return true;

        default:
          sysAbort(s);
        }
      }
    }

    virtual void acquire(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ != t) {
        int r UNUSED = WaitForSingleObject(mutex, INFINITE);
        assertT(s, r == WAIT_OBJECT_0);
        owner_ = t;
      }
      ++depth;
    }

    virtual void release(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ == t) {
        if (--depth == 0) {
          owner_ = 0;
          bool success UNUSED = ReleaseMutex(mutex);
          assertT(s, success);
        }
      } else {
        sysAbort(s);
      }
    }

    void append(Thread* t)
    {
#ifndef NDEBUG
      for (Thread* x = first; x; x = x->next) {
        expect(s, t != x);
      }
#endif

      if (last) {
        last->next = t;
        last = t;
      } else {
        first = last = t;
      }
    }

    void remove(Thread* t)
    {
      Thread* previous = 0;
      for (Thread* current = first; current;) {
        if (t == current) {
          if (current == first) {
            first = t->next;
          } else {
            previous->next = t->next;
          }

          if (current == last) {
            last = previous;
          }

          t->next = 0;

          break;
        } else {
          previous = current;
          current = current->next;
        }
      }

#ifndef NDEBUG
      for (Thread* x = first; x; x = x->next) {
        expect(s, t != x);
      }
#endif
    }

    virtual void wait(System::Thread* context, int64_t time)
    {
      wait(context, time, false);
    }

    virtual bool waitAndClearInterrupted(System::Thread* context, int64_t time)
    {
      return wait(context, time, true);
    }

    bool wait(System::Thread* context, int64_t time, bool clearInterrupted)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ == t) {
        // Initialized here to make gcc 4.2 a happy compiler
        bool interrupted = false;
        bool notified = false;
        unsigned depth = 0;

        int r UNUSED;

        {
          ACQUIRE(s, t->mutex);

          expect(s, (t->flags & Notified) == 0);

          interrupted = t->r->interrupted();
          if (interrupted and clearInterrupted) {
            t->r->setInterrupted(false);
          }

          t->flags |= Waiting;

          append(t);

          depth = this->depth;
          this->depth = 0;
          owner_ = 0;

          bool success UNUSED = ReleaseMutex(mutex);
          assertT(s, success);

          if (not interrupted) {
            success = ResetEvent(t->event);
            assertT(s, success);

            success = ReleaseMutex(t->mutex);
            assertT(s, success);

            r = WaitForSingleObject(t->event, (time ? time : INFINITE));
            assertT(s, r == WAIT_OBJECT_0 or r == WAIT_TIMEOUT);

            r = WaitForSingleObject(t->mutex, INFINITE);
            assertT(s, r == WAIT_OBJECT_0);

            interrupted = t->r->interrupted();
            if (interrupted and clearInterrupted) {
              t->r->setInterrupted(false);
            }
          }

          notified = ((t->flags & Notified) != 0);
        }

        r = WaitForSingleObject(mutex, INFINITE);
        assertT(s, r == WAIT_OBJECT_0);

        {
          ACQUIRE(s, t->mutex);
          t->flags = 0;
        }

        if (not notified) {
          remove(t);
        } else {
#ifndef NDEBUG
          for (Thread* x = first; x; x = x->next) {
            expect(s, t != x);
          }
#endif
        }

        t->next = 0;

        owner_ = t;
        this->depth = depth;

        return interrupted;
      } else {
        sysAbort(s);
      }
    }

    void doNotify(Thread* t)
    {
      ACQUIRE(s, t->mutex);

      t->flags |= Notified;

      bool success UNUSED = SetEvent(t->event);
      assertT(s, success);
    }

    virtual void notify(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ == t) {
        if (first) {
          Thread* t = first;
          first = first->next;
          if (t == last) {
            expect(s, first == 0);
            last = 0;
          }

          doNotify(t);
        }
      } else {
        sysAbort(s);
      }
    }

    virtual void notifyAll(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);
      assertT(s, t);

      if (owner_ == t) {
        for (Thread* t = first; t; t = t->next) {
          doNotify(t);
        }
        first = last = 0;
      } else {
        sysAbort(s);
      }
    }

    virtual System::Thread* owner()
    {
      return owner_;
    }

    virtual void dispose()
    {
      assertT(s, owner_ == 0);
      CloseHandle(mutex);
      ::free(this);
    }

    System* s;
    HANDLE mutex;
    Thread* owner_;
    Thread* first;
    Thread* last;
    unsigned depth;
  };

  class Local : public System::Local {
   public:
    Local(System* s) : s(s)
    {
      key = TlsAlloc();
      assertT(s, key != TLS_OUT_OF_INDEXES);
    }

    virtual void* get()
    {
      return TlsGetValue(key);
    }

    virtual void set(void* p)
    {
      bool r UNUSED = TlsSetValue(key, p);
      assertT(s, r);
    }

    virtual void dispose()
    {
      bool r UNUSED = TlsFree(key);
      assertT(s, r);

      ::free(this);
    }

    System* s;
    unsigned key;
  };

  class Region : public System::Region {
   public:
    Region(System* system,
           uint8_t* start,
           size_t length,
           HANDLE mapping,
           HANDLE file)
        : system(system),
          start_(start),
          length_(length),
          mapping(mapping),
          file(file)
    {
    }

    virtual const uint8_t* start()
    {
      return start_;
    }

    virtual size_t length()
    {
      return length_;
    }

    virtual void dispose()
    {
      if (start_) {
        if (start_)
          UnmapViewOfFile(start_);
        if (mapping)
          CloseHandle(mapping);
        if (file)
          CloseHandle(file);
      }
      system->free(this);
    }

    System* system;
    uint8_t* start_;
    size_t length_;
    HANDLE mapping;
    HANDLE file;
  };

  class Directory : public System::Directory {
   public:
    Directory(System* s) : s(s), handle(0), findNext(false)
    {
    }

    virtual const char* next()
    {
      if (handle and handle != INVALID_HANDLE_VALUE) {
        if (findNext) {
          if (FindNextFile(handle, &data)) {
            return data.cFileName;
          }
        } else {
          findNext = true;
          return data.cFileName;
        }
      }
      return 0;
    }

    virtual void dispose()
    {
      if (handle and handle != INVALID_HANDLE_VALUE) {
        FindClose(handle);
      }
      ::free(this);
    }

    System* s;
    HANDLE handle;
    WIN32_FIND_DATA data;
    bool findNext;
  };

  class Library : public System::Library {
   public:
    Library(System* s, HMODULE handle, const char* name)
        : s(s), handle(handle), name_(name), next_(0)
    {
    }

    virtual void* resolve(const char* function)
    {
      void* address;
      FARPROC p = GetProcAddress(handle, function);
      memcpy(&address, &p, BytesPerWord);
      return address;
    }

    virtual const char* name()
    {
      return name_;
    }

    virtual System::Library* next()
    {
      return next_;
    }

    virtual void setNext(System::Library* lib)
    {
      next_ = lib;
    }

    virtual void disposeAll()
    {
      if (Verbose) {
        fprintf(stderr, "close %p\n", handle);
        fflush(stderr);
      }

      if (name_) {
        FreeLibrary(handle);
      }

      if (next_) {
        next_->disposeAll();
      }

      if (name_) {
        ::free(const_cast<char*>(name_));
      }

      ::free(this);
    }

    System* s;
    HMODULE handle;
    const char* name_;
    System::Library* next_;
  };

  MySystem(bool reentrant): reentrant(reentrant)
  {
    if (not reentrant) {
      expect(this, globalSystem == 0);
      globalSystem = this;
    }

    mutex = CreateMutex(0, false, 0);
    assertT(this, mutex);
  }

  virtual void* tryAllocate(size_t sizeInBytes)
  {
    return malloc(sizeInBytes);
  }

  virtual void free(const void* p)
  {
    if (p)
      ::free(const_cast<void*>(p));
  }

  virtual bool success(Status s) {
    return s == 0;
  }

  virtual Status attach(Runnable* r)
  {
    Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
    bool success UNUSED = DuplicateHandle(GetCurrentProcess(),
                                          GetCurrentThread(),
                                          GetCurrentProcess(),
                                          &(t->thread),
                                          0,
                                          false,
                                          DUPLICATE_SAME_ACCESS);
    assertT(this, success);
    r->attach(t);
    return 0;
  }

  virtual Status start(Runnable* r)
  {
    Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
    r->attach(t);
    DWORD id;
    t->thread = CreateThread(0, 0, run, r, 0, &id);
    assertT(this, t->thread);
    return 0;
  }

  virtual Status make(System::Mutex** m)
  {
    *m = new (allocate(this, sizeof(Mutex))) Mutex(this);
    return 0;
  }

  virtual Status make(System::Monitor** m)
  {
    *m = new (allocate(this, sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual Status make(System::Local** l)
  {
    *l = new (allocate(this, sizeof(Local))) Local(this);
    return 0;
  }

  virtual Status visit(System::Thread* st UNUSED,
                       System::Thread* sTarget,
                       ThreadVisitor* visitor)
  {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    assertT(this, st != sTarget);

    Thread* target = static_cast<Thread*>(sTarget);

    ACQUIRE(this, mutex);

    bool success = false;
    int rv = SuspendThread(target->thread);
    if (rv != -1) {
      CONTEXT context;
      memset(&context, 0, sizeof(CONTEXT));
      context.ContextFlags = CONTEXT_CONTROL;
      rv = GetThreadContext(target->thread, &context);

      if (rv) {
#ifdef ARCH_x86_32
        visitor->visit(reinterpret_cast<void*>(context.Eip),
                       reinterpret_cast<void*>(context.Esp),
                       reinterpret_cast<void*>(context.Ebp));
#elif defined ARCH_x86_64
        visitor->visit(reinterpret_cast<void*>(context.Rip),
                       reinterpret_cast<void*>(context.Rsp),
                       reinterpret_cast<void*>(context.Rbp));
#endif
        success = true;
      }

      rv = ResumeThread(target->thread);
      expect(this, rv != -1);
    }

    return (success ? 0 : 1);
#else
#pragma message( \
    "TODO: http://msdn.microsoft.com/en-us/library/windowsphone/develop/system.windows.application.unhandledexception(v=vs.105).aspx")
    return false;
#endif
  }

  virtual Status map(System::Region** region, const char* name)
  {
    Status status = 1;
    size_t nameLen = strlen(name) * 2;
    RUNTIME_ARRAY(wchar_t, wideName, nameLen + 1);
    MultiByteToWideChar(
        CP_UTF8, 0, name, -1, RUNTIME_ARRAY_BODY(wideName), nameLen + 1);
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    HANDLE file = CreateFileW(RUNTIME_ARRAY_BODY(wideName),
                              FILE_READ_DATA,
                              FILE_SHARE_READ,
                              0,
                              OPEN_EXISTING,
                              0,
                              0);
#else
    HANDLE file = CreateFile2(RUNTIME_ARRAY_BODY(wideName),
                              GENERIC_READ,
                              FILE_SHARE_READ,
                              OPEN_EXISTING,
                              0);
#endif
    if (file != INVALID_HANDLE_VALUE) {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
      unsigned size = GetFileSize(file, 0);
#else
      FILE_STANDARD_INFO info;
      unsigned size = INVALID_FILE_SIZE;
      if (GetFileInformationByHandleEx(
              file, FileStandardInfo, &info, sizeof(info)))
        size = info.EndOfFile.QuadPart;
#endif
      if (size != INVALID_FILE_SIZE) {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
        HANDLE mapping = CreateFileMapping(file, 0, PAGE_READONLY, 0, size, 0);
#else
        HANDLE mapping
            = CreateFileMappingFromApp(file, 0, PAGE_READONLY, size, 0);
#endif
        if (mapping) {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
          void* data = MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, 0);
#else
          void* data = MapViewOfFileFromApp(mapping, FILE_MAP_READ, 0, 0);
#endif
          if (data) {
            *region = new (allocate(this, sizeof(Region)))
                Region(this, static_cast<uint8_t*>(data), size, file, mapping);
            status = 0;
          }

          if (status) {
            CloseHandle(mapping);
          }
        }
      }

      if (status) {
        CloseHandle(file);
      }
    }

    return status;
  }

  virtual Status open(System::Directory** directory, const char* name)
  {
    Status status = 1;

    unsigned length = strlen(name);
    RUNTIME_ARRAY(char, buffer, length + 3);
    memcpy(RUNTIME_ARRAY_BODY(buffer), name, length);
    memcpy(RUNTIME_ARRAY_BODY(buffer) + length, "\\*", 3);

    Directory* d = new (allocate(this, sizeof(Directory))) Directory(this);

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    d->handle = FindFirstFile(RUNTIME_ARRAY_BODY(buffer), &(d->data));
#else
    d->handle = FindFirstFileEx(RUNTIME_ARRAY_BODY(buffer),
                                FindExInfoStandard,
                                &(d->data),
                                FindExSearchNameMatch,
                                0,
                                0);
#endif
    if (d->handle == INVALID_HANDLE_VALUE) {
      d->dispose();
    } else {
      *directory = d;
      status = 0;
    }

    return status;
  }

  virtual FileType stat(const char* name, size_t* length)
  {
    size_t nameLen = strlen(name) * 2;
    RUNTIME_ARRAY(wchar_t, wideName, nameLen + 1);
    MultiByteToWideChar(
        CP_UTF8, 0, name, -1, RUNTIME_ARRAY_BODY(wideName), nameLen + 1);
    WIN32_FILE_ATTRIBUTE_DATA data;
    if (GetFileAttributesExW(
            RUNTIME_ARRAY_BODY(wideName), GetFileExInfoStandard, &data)) {
      if (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
        return TypeDirectory;
      } else {
        *length = (data.nFileSizeHigh * static_cast<int64_t>(MAXDWORD + 1))
                  + data.nFileSizeLow;
        return TypeFile;
      }
    } else {
      return TypeDoesNotExist;
    }
  }

  virtual const char* libraryPrefix()
  {
    return SO_PREFIX;
  }

  virtual const char* librarySuffix()
  {
    return SO_SUFFIX;
  }

  virtual const char* toAbsolutePath(avian::util::AllocOnly* allocator,
                                     const char* name)
  {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    if (strncmp(name, "//", 2) == 0 or strncmp(name, "\\\\", 2) == 0
        or strncmp(name + 1, ":/", 2) == 0
        or strncmp(name + 1, ":\\", 2) == 0) {
      return copy(allocator, name);
    } else {
      TCHAR buffer[MAX_PATH];
      GetCurrentDirectory(MAX_PATH, buffer);
      return append(allocator, buffer, "\\", name);
    }
#else
#pragma message( \
    "TODO:http://lunarfrog.com/blog/2012/05/21/winrt-folders-access/ Windows.ApplicationModel.Package.Current.InstalledLocation")
    return copy(allocator, name);
#endif
  }

  virtual Status load(System::Library** lib, const char* name)
  {
    HMODULE handle;
    unsigned nameLength = (name ? strlen(name) : 0);
    if (name) {
      size_t nameLen = nameLength * 2;
      RUNTIME_ARRAY(wchar_t, wideName, nameLen + 1);
      MultiByteToWideChar(
          CP_UTF8, 0, name, -1, RUNTIME_ARRAY_BODY(wideName), nameLen + 1);

#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
      handle = LoadLibraryW(RUNTIME_ARRAY_BODY(wideName));
#else
      handle = LoadPackagedLibrary(RUNTIME_ARRAY_BODY(wideName), 0);
#endif
    } else {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
      handle = GetModuleHandle(0);
#else
      // Most of WinRT/WP8 applications can not host native object files inside
      // main executable
      assertT(this, false);
#endif
    }

    if (handle) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", name, handle);
        fflush(stderr);
      }

      char* n;
      if (name) {
        n = static_cast<char*>(allocate(this, nameLength + 1));
        memcpy(n, name, nameLength + 1);
      } else {
        n = 0;
      }

      *lib = new (allocate(this, sizeof(Library))) Library(this, handle, n);

      return 0;
    } else {
      if (Verbose) {
        fprintf(stderr, "unable to open %s: %ld\n", name, GetLastError());
        fflush(stderr);
      }

      return 1;
    }
  }

  virtual char pathSeparator()
  {
    return ';';
  }

  virtual char fileSeparator()
  {
    return '\\';
  }

  virtual int64_t now()
  {
    // We used to use _ftime here, but that only gives us 1-second
    // resolution on Windows 7.  _ftime_s might work better, but MinGW
    // doesn't have it as of this writing.  So we use this mess instead:
    FILETIME time;
    GetSystemTimeAsFileTime(&time);
    return (((static_cast<int64_t>(time.dwHighDateTime) << 32)
             | time.dwLowDateTime) / 10000) - 11644473600000LL;
  }

  virtual void yield()
  {
#if !defined(WINAPI_FAMILY) || WINAPI_FAMILY_PARTITION(WINAPI_PARTITION_DESKTOP)
    SwitchToThread();
#else
    YieldProcessor();
#endif
  }

  virtual void exit(int code)
  {
    ::exit(code);
  }

  virtual void abort()
  {
    avian::system::crash();
  }

  virtual void dispose()
  {
    if (not reentrant) {
      globalSystem = 0;
    }
    
    CloseHandle(mutex);
    ::free(this);
  }

  HANDLE mutex;
  bool reentrant;
};

}  // namespace

namespace vm {

AVIAN_EXPORT System* makeSystem(bool reentrant)
{
  return new (malloc(sizeof(MySystem))) MySystem(reentrant);
}

}  // namespace vm
