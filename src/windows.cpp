#include "sys/stat.h"
#include "windows.h"

#undef max
#undef min

#include "x86.h"
#include "system.h"

#define ACQUIRE(s, x) MutexResource MAKE_NAME(mutexResource_) (s, x)

using namespace vm;

namespace {

class MutexResource {
 public:
  MutexResource(System* s, HANDLE m): s(s), m(m) {
    int r UNUSED = WaitForSingleObject(m, INFINITE);
    assert(s, r == WAIT_OBJECT_0);
  }

  ~MutexResource() {
    int r UNUSED = ReleaseMutex(m);
    assert(s, r == 0);
  }

 private:
  System* s; 
  HANDLE m;
};

DWORD WINAPI
run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
  return 0;
}

const bool Verbose = false;

const unsigned Waiting = 1 << 0;
const unsigned Notified = 1 << 1;

class MySystem: public System {
 public:
  class Thread: public System::Thread {
   public:
    Thread(System* s, System::Runnable* r):
      s(s),
      r(r),
      next(0),
      flags(0)
    {
      mutex = CreateMutex(0, false, 0);
      assert(s, mutex);

      event = CreateEvent(0, true, false, 0);
      assert(s, event);
    }

    virtual void interrupt() {
      ACQUIRE(s, mutex);

      r->setInterrupted(true);

      if (flags & Waiting) {
        int r UNUSED = SetEvent(event);
        assert(s, r == 0);
      }
    }

    virtual void join() {
      int r UNUSED = WaitForSingleObject(thread, INFINITE);
      assert(s, r == WAIT_OBJECT_0);
    }

    virtual void dispose() {
      CloseHandle(event);
      CloseHandle(mutex);
      CloseHandle(thread);
      s->free(this);
    }

    HANDLE thread;
    HANDLE mutex;
    HANDLE event;
    System* s;
    System::Runnable* r;
    Thread* next;
    unsigned flags;
  };

  class Monitor: public System::Monitor {
   public:
    Monitor(System* s): s(s), owner_(0), first(0), last(0), depth(0) {
      mutex = CreateMutex(0, false, 0);
      assert(s, mutex);
    }

    virtual bool tryAcquire(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        ++ depth;
        return true;
      } else {
        switch (WaitForSingleObject(mutex, 0)) {
        case WAIT_TIMEOUT:
          return false;

        case WAIT_OBJECT_0:
          owner_ = t;
          ++ depth;
          return true;

        default:
          sysAbort(s);
        }
      }
    }

    virtual void acquire(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ != t) {
        int r UNUSED = WaitForSingleObject(mutex, INFINITE);
        assert(s, r == WAIT_OBJECT_0);
        owner_ = t;
      }
      ++ depth;
    }

    virtual void release(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        if (-- depth == 0) {
          owner_ = 0;
          int r UNUSED = ReleaseMutex(mutex);
          assert(s, r == 0);
        }
      } else {
        sysAbort(s);
      }
    }

    void append(Thread* t) {
      if (last) {
        last->next = t;
      } else {
        first = last = t;
      }
    }

    void remove(Thread* t) {
      for (Thread** p = &first; *p;) {
        if (t == *p) {
          *p = t->next;
          if (last == t) {
            last = 0;
          }
          break;
        } else {
          p = &((*p)->next);
        }
      }
    }

    virtual bool wait(System::Thread* context, int64_t time) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        ACQUIRE(s, t->mutex);
      
        if (t->r->interrupted()) {
          t->r->setInterrupted(false);
          return true;
        }

        t->flags |= Waiting;

        append(t);

        unsigned depth = this->depth;
        this->depth = 0;
        owner_ = 0;

        int r UNUSED = ReleaseMutex(mutex);
        assert(s, r == 0);

        r = ResetEvent(t->event);
        assert(s, r);

        r = ReleaseMutex(t->mutex);
        assert(s, r == 0);

        r = WaitForSingleObject(t->event, (time ? time : INFINITE));
        assert(s, r == WAIT_OBJECT_0);

        r = WaitForSingleObject(t->mutex, INFINITE);
        assert(s, r == WAIT_OBJECT_0);

        r = WaitForSingleObject(mutex, INFINITE);
        assert(s, r == WAIT_OBJECT_0);

        owner_ = t;
        this->depth = depth;
        
        if ((t->flags & Notified) == 0) {
          remove(t);
        }

        t->flags = 0;
        t->next = 0;

        if (t->r->interrupted()) {
          t->r->setInterrupted(false);
          return true;
        } else {
          return false;
        }
      } else {
        sysAbort(s);
      }
    }

    void doNotify(Thread* t) {
      ACQUIRE(s, t->mutex);

      t->flags |= Notified;

      int r UNUSED = SetEvent(t->event);
      assert(s, r == 0);
    }

    virtual void notify(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        if (first) {
          Thread* t = first;
          first = first->next;
          if (t == last) {
            last = 0;
          }

          doNotify(t);
        }
      } else {
        sysAbort(s);
      }
    }

    virtual void notifyAll(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        for (Thread* t = first; t; t = t->next) {
          doNotify(t);
        }
        first = last = 0;
      } else {
        sysAbort(s);
      }
    }
    
    virtual System::Thread* owner() {
      return owner_;
    }

    virtual void dispose() {
      assert(s, owner_ == 0);
      CloseHandle(mutex);
      s->free(this);
    }

    System* s;
    HANDLE mutex;
    Thread* owner_;
    Thread* first;
    Thread* last;
    unsigned depth;
  };

  class Local: public System::Local {
   public:
    Local(System* s): s(s) {
      key = TlsAlloc();
      assert(s, key == TLS_OUT_OF_INDEXES);
    }

    virtual void* get() {
      return TlsGetValue(key);
    }

    virtual void set(void* p) {
      bool r UNUSED = TlsSetValue(key, p);
      assert(s, r);
    }

    virtual void dispose() {
      bool r UNUSED = TlsFree(key);
      assert(s, r);

      s->free(this);
    }

    System* s;
    unsigned key;
  };

  class Region: public System::Region {
   public:
    Region(System* system, uint8_t* start, size_t length, HANDLE mapping,
           HANDLE file):
      system(system),
      start_(start),
      length_(length),
      mapping(mapping),
      file(file)
    { }

    virtual const uint8_t* start() {
      return start_;
    }

    virtual size_t length() {
      return length_;
    }

    virtual void dispose() {
      if (start_) {
        if (start_) UnmapViewOfFile(start_);
        if (mapping) CloseHandle(mapping);
        if (file) CloseHandle(file);
      }
      system->free(this);
    }

    System* system;
    uint8_t* start_;
    size_t length_;
    HANDLE mapping;
    HANDLE file;
  };

  class Library: public System::Library {
   public:
    Library(System* s, HMODULE handle, const char* name, bool mapName,
            System::Library* next):
      s(s),
      handle(handle),
      name_(name),
      mapName_(mapName),
      next_(next)
    { }

    virtual void* resolve(const char* function) {
      void* address;
      FARPROC p = GetProcAddress(handle, function);
      memcpy(&address, &p, BytesPerWord);
      return address;
    }

    virtual const char* name() {
      return name_;
    }

    virtual bool mapName() {
      return mapName_;
    }

    virtual System::Library* next() {
      return next_;
    }

    virtual void dispose() {
      if (Verbose) {
        fprintf(stderr, "close %p\n", handle);
      }

      FreeLibrary(handle);

      if (next_) {
        next_->dispose();
      }

      if (name_) {
        s->free(name_);
      }

      s->free(this);
    }

    System* s;
    HMODULE handle;
    const char* name_;
    bool mapName_;
    System::Library* next_;
  };

  MySystem(unsigned limit): limit(limit), count(0) {
    mutex = CreateMutex(0, false, 0);
    assert(this, mutex);
  }

  virtual bool success(Status s) {
    return s == 0;
  }

  virtual void* tryAllocate(unsigned size) {
    ACQUIRE(this, mutex);

    if (Verbose) {
      fprintf(stderr, "try %d; count: %d; limit: %d\n",
              size, count, limit);
    }

    if (count + size > limit) {
      return 0;
    } else {
      uintptr_t* up = static_cast<uintptr_t*>
        (malloc(size + sizeof(uintptr_t)));
      if (up == 0) {
        sysAbort(this);
      } else {
        *up = size;
        count += *up;
      
        return up + 1;
      }
    }
  }

  virtual void free(const void* p) {
    ACQUIRE(this, mutex);

    if (p) {
      const uintptr_t* up = static_cast<const uintptr_t*>(p) - 1;
      if (count < *up) {
        abort();
      }
      count -= *up;

      if (Verbose) {
        fprintf(stderr, "free %"ULD"; count: %d; limit: %d\n",
                *up, count, limit);
      }

      ::free(const_cast<uintptr_t*>(up));
    }
  }

  virtual Status attach(Runnable* r) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, r);
    bool success = DuplicateHandle
      (GetCurrentProcess(), GetCurrentThread(), GetCurrentProcess(),
       &(t->thread), 0, false, DUPLICATE_SAME_ACCESS);
    assert(this, success);
    r->attach(t);
    return 0;
  }

  virtual Status start(Runnable* r) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, r);
    r->attach(t);
    DWORD id;
    t->thread = CreateThread(0, 0, run, r, 0, &id);
    assert(this, t->thread);
    return 0;
  }

  virtual Status make(System::Monitor** m) {
    *m = new (System::allocate(sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual Status make(System::Local** l) {
    *l = new (System::allocate(sizeof(Local))) Local(this);
    return 0;
  }

  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size, unsigned returnType)
  {
    return dynamicCall(function, arguments, types, count, size, returnType);
  }

  virtual Status map(System::Region** region, const char* name) {
    Status status = 1;

    HANDLE file = CreateFile(name, FILE_READ_DATA, 0, 0, OPEN_EXISTING, 0, 0);
    if (file != INVALID_HANDLE_VALUE) {
      unsigned size = GetFileSize(file, 0);
      if (size != INVALID_FILE_SIZE) {
        HANDLE mapping = CreateFileMapping(file, 0, PAGE_READONLY, 0, size, 0);
        if (mapping) {
          void* data = MapViewOfFile(mapping, FILE_MAP_READ, 0, 0, 0);
          if (data) {
            *region = new (allocate(sizeof(Region)))
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

  virtual FileType identify(const char* name) {
    struct _stat s;
    int r = _stat(name, &s);
    if (r == 0) {
      if (S_ISREG(s.st_mode)) {
        return File;
      } else if (S_ISDIR(s.st_mode)) {
        return Directory;
      } else {
        return Unknown;
      }
    } else {
      return DoesNotExist;
    }
  }

  virtual Status load(System::Library** lib,
                      const char* name,
                      bool mapName,
                      System::Library* next)
  {
    HMODULE handle;
    unsigned nameLength = (name ? strlen(name) : 0);
    if (mapName) {
      unsigned size = nameLength + sizeof(SO_SUFFIX);
      char buffer[size];
      snprintf(buffer, size, "%s" SO_SUFFIX, name);
      handle = LoadLibrary(buffer);
    } else {
      handle = LoadLibrary(name);
    }
 
    if (handle) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", name, handle);
      }

      char* n;
      if (name) {
        n = static_cast<char*>(System::allocate(nameLength + 1));
        memcpy(n, name, nameLength + 1);
      } else {
        n = 0;
      }

      *lib = new (System::allocate(sizeof(Library)))
        Library(this, handle, n, mapName, next);
      return 0;
    } else {
//       fprintf(stderr, "dlerror: %s\n", dlerror());
      return 1;
    }
  }


  virtual int64_t now() {
    static LARGE_INTEGER frequency;
    static LARGE_INTEGER time;
    static bool init = true;

    if (init) {
      QueryPerformanceFrequency(&frequency);

      if (frequency.QuadPart == 0) {
        return 0;      
      }

      init = false;
    }

    QueryPerformanceCounter(&time);
    return static_cast<int64_t>
      (((static_cast<double>(time.QuadPart)) * 1000.0) /
       (static_cast<double>(frequency.QuadPart)));
  }

  virtual void exit(int code) {
    ::exit(code);
  }

  virtual void abort() {
    ::abort();
  }

  virtual void dispose() {
    CloseHandle(mutex);
    ::free(this);
  }

  HANDLE mutex;
  unsigned limit;
  unsigned count;
};

} // namespace

namespace vm {

System*
makeSystem(unsigned heapSize)
{
  return new (malloc(sizeof(MySystem))) MySystem(heapSize);
}

} // namespace vm
