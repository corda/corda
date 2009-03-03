/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifdef __APPLE__
#  include "CoreFoundation/CoreFoundation.h"
#  undef assert
#endif

#include "sys/mman.h"
#include "sys/types.h"
#include "sys/stat.h"
#include "sys/time.h"
#include "time.h"
#include "fcntl.h"
#include "dlfcn.h"
#include "errno.h"
#include "unistd.h"
#include "pthread.h"
#include "signal.h"
#include "ucontext.h"
#include "stdint.h"
#include "dirent.h"

#include "arch.h"
#include "system.h"

#define ACQUIRE(x) MutexResource MAKE_NAME(mutexResource_) (x)

using namespace vm;

namespace {

class MutexResource {
 public:
  MutexResource(pthread_mutex_t& m): m(&m) {
    pthread_mutex_lock(&m);
  }

  ~MutexResource() {
    pthread_mutex_unlock(m);
  }

 private:
  pthread_mutex_t* m;
};

const int VisitSignal = SIGUSR1;
#ifdef __APPLE__
const int SegFaultSignal = SIGBUS;
#else
const int SegFaultSignal = SIGSEGV;
#endif
const int InterruptSignal = SIGUSR2;

const unsigned VisitSignalIndex = 0;
const unsigned SegFaultSignalIndex = 1;
const unsigned InterruptSignalIndex = 2;

class MySystem;
MySystem* system;

const int signals[] = { VisitSignal, SegFaultSignal, InterruptSignal };

void
handleSignal(int signal, siginfo_t* info, void* context);

void*
run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
  return 0;
}

void
pathOfExecutable(System* s, const char** retBuf, unsigned* size)
{
#ifdef __APPLE__
  CFBundleRef bundle = CFBundleGetMainBundle();
  CFURLRef url = CFBundleCopyExecutableURL(bundle);
  CFStringRef path = CFURLCopyPath(url);
  CFIndex pathSize = CFStringGetMaximumSizeOfFileSystemRepresentation(path);
  char* buffer = reinterpret_cast<char*>(allocate(s, pathSize));
  if (CFStringGetFileSystemRepresentation(path, buffer, pathSize)) {
    *size = pathSize;
    *retBuf = buffer;
  } else {
    abort();
  }
#else
  if (s)
    *size = 0;
  *retBuf = NULL;
#endif
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
      pthread_mutex_init(&mutex, 0);
      pthread_cond_init(&condition, 0);
    }

    virtual void interrupt() {
      ACQUIRE(mutex);

      r->setInterrupted(true);

      if (flags & Waiting) {
        pthread_kill(thread, InterruptSignal);
      }
    }

    virtual void join() {
      int rv UNUSED = pthread_join(thread, 0);
      expect(s, rv == 0);
    }

    virtual void dispose() {
      s->free(this);
    }

    pthread_t thread;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    System* s;
    System::Runnable* r;
    Thread* next;
    unsigned flags;
  };

  class Mutex: public System::Mutex {
   public:
    Mutex(System* s): s(s) {
      pthread_mutex_init(&mutex, 0);    
    }

    virtual void acquire() {
      pthread_mutex_lock(&mutex);
    }

    virtual void release() {
      pthread_mutex_unlock(&mutex);
    }

    virtual void dispose() {
      pthread_mutex_destroy(&mutex);
      s->free(this);
    }

    System* s;
    pthread_mutex_t mutex;
  };

  class Monitor: public System::Monitor {
   public:
    Monitor(System* s): s(s), owner_(0), first(0), last(0), depth(0) {
      pthread_mutex_init(&mutex, 0);    
    }

    virtual bool tryAcquire(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        ++ depth;
        return true;
      } else {
        switch (pthread_mutex_trylock(&mutex)) {
        case EBUSY:
          return false;

        case 0:
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
        pthread_mutex_lock(&mutex);
        owner_ = t;
      }
      ++ depth;
    }

    virtual void release(System::Thread* context) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        if (-- depth == 0) {
          owner_ = 0;
          pthread_mutex_unlock(&mutex);
        }
      } else {
        sysAbort(s);
      }
    }

    void append(Thread* t) {
      if (last) {
        last->next = t;
        last = t;
      } else {
        first = last = t;
      }
    }

    void remove(Thread* t) {
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
    }

    virtual bool wait(System::Thread* context, int64_t time) {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        // Initialized here to make gcc 4.2 a happy compiler
        bool interrupted = false;
        bool notified = false;
        unsigned depth = 0;

        { ACQUIRE(t->mutex);
      
          if (t->r->interrupted()) {
            t->r->setInterrupted(false);
            return true;
          }

          t->flags |= Waiting;

          append(t);

          depth = this->depth;
          this->depth = 0;
          owner_ = 0;
          pthread_mutex_unlock(&mutex);

          if (time) {
            int64_t then = s->now() + time;
            timespec ts = { then / 1000, (then % 1000) * 1000 * 1000 };
            int rv UNUSED = pthread_cond_timedwait
              (&(t->condition), &(t->mutex), &ts);
            expect(s, rv == 0 or rv == ETIMEDOUT or rv == EINTR);
          } else {
            int rv UNUSED = pthread_cond_wait(&(t->condition), &(t->mutex));
            expect(s, rv == 0 or rv == EINTR);
          }

          notified = ((t->flags & Notified) != 0);
        
          t->flags = 0;

          interrupted = t->r->interrupted();
          if (interrupted) {
            t->r->setInterrupted(false);
          }
        }

        pthread_mutex_lock(&mutex);

        if (not notified) {
          remove(t);
        }

        t->next = 0;

        owner_ = t;
        this->depth = depth;

        return interrupted;
      } else {
        sysAbort(s);
      }
    }

    void doNotify(Thread* t) {
      ACQUIRE(t->mutex);

      t->flags |= Notified;
      int rv UNUSED = pthread_cond_signal(&(t->condition));
      expect(s, rv == 0);
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
      expect(s, owner_ == 0);
      pthread_mutex_destroy(&mutex);
      s->free(this);
    }

    System* s;
    pthread_mutex_t mutex;
    Thread* owner_;
    Thread* first;
    Thread* last;
    unsigned depth;
  };

  class Local: public System::Local {
   public:
    Local(System* s): s(s) {
      int r UNUSED = pthread_key_create(&key, 0);
      expect(s, r == 0);
    }

    virtual void* get() {
      return pthread_getspecific(key);
    }

    virtual void set(void* p) {
      int r UNUSED = pthread_setspecific(key, p);
      expect(s, r == 0);
    }

    virtual void dispose() {
      int r UNUSED = pthread_key_delete(key);
      expect(s, r == 0);

      s->free(this);
    }

    System* s;
    pthread_key_t key;
  };

  class Region: public System::Region {
   public:
    Region(System* s, uint8_t* start, size_t length):
      s(s),
      start_(start),
      length_(length)
    { }

    virtual const uint8_t* start() {
      return start_;
    }

    virtual size_t length() {
      return length_;
    }

    virtual void dispose() {
      if (start_) {
        munmap(start_, length_);
      }
      s->free(this);
    }

    System* s;
    uint8_t* start_;
    size_t length_;
  };

  class Directory: public System::Directory {
   public:
    Directory(System* s, DIR* directory): s(s), directory(directory) { }

    virtual const char* next() {
      if (directory) {
        dirent* e = readdir(directory);
        if (e) {
          return e->d_name;
        }
      }
      return 0;
    }

    virtual void dispose() {
      if (directory) {
        closedir(directory);
      }
      s->free(this);
    }

    System* s;
    DIR* directory;
  };

  class Library: public System::Library {
   public:
    Library(System* s, void* p, const char* name, unsigned nameLength,
            bool mapName, bool isMain):
      s(s),
      p(p),
      mainExecutable(isMain),
      name_(name),
      nameLength(nameLength),
      mapName_(mapName),
      next_(0)
    { }

    virtual void* resolve(const char* function) {
      return dlsym(p, function);
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

    virtual void setNext(System::Library* lib) {
      next_ = lib;
    }

    virtual void disposeAll() {
      if (Verbose) {
        fprintf(stderr, "close %p\n", p);
      }

      if (!mainExecutable) dlclose(p);

      if (next_) {
        next_->disposeAll();
      }

      if (name_) {
        s->free(name_);
      }

      s->free(this);
    }

    System* s;
    void* p;
    bool mainExecutable;
    const char* name_;
    unsigned nameLength;
    bool mapName_;
    System::Library* next_;
  };

  MySystem(): threadVisitor(0), visitTarget(0) {
    expect(this, system == 0);
    system = this;

    registerHandler(&nullHandler, InterruptSignalIndex);
    registerHandler(&nullHandler, VisitSignalIndex);

    expect(this, make(&visitLock) == 0);
  }

  int registerHandler(System::SignalHandler* handler, int index) {
    if (handler) {
      handlers[index] = handler;

      struct sigaction sa;
      memset(&sa, 0, sizeof(struct sigaction));
      sigemptyset(&(sa.sa_mask));
      sa.sa_flags = SA_SIGINFO;
      sa.sa_sigaction = handleSignal;
    
      return sigaction(signals[index], &sa, oldHandlers + index);
    } else if (handlers[index]) {
      handlers[index] = 0;
      return sigaction(signals[index], oldHandlers + index, 0);
    } else {
      return 1;
    }
  }

  virtual void* tryAllocate(unsigned sizeInBytes) {
    return malloc(sizeInBytes);
  }

  virtual void free(const void* p) {
    if (p) ::free(const_cast<void*>(p));
  }

  virtual void* tryAllocateExecutable(unsigned sizeInBytes) {
    assert(this, sizeInBytes % LikelyPageSizeInBytes == 0);

#ifdef __x86_64__
    const unsigned Extra = MAP_32BIT;
#else
    const unsigned Extra = 0;
#endif

    void* p = mmap(0, sizeInBytes, PROT_EXEC | PROT_READ | PROT_WRITE,
                   MAP_PRIVATE | MAP_ANON | Extra, -1, 0);
    
    if (p == MAP_FAILED) {
      return 0;
    } else {
      return p;
    }
  }

  virtual void freeExecutable(const void* p, unsigned sizeInBytes) {
    assert(this, sizeInBytes % LikelyPageSizeInBytes == 0);

    int r UNUSED = munmap(const_cast<void*>(p), sizeInBytes);
    assert(this, r == 0);
  }

  virtual bool success(Status s) {
    return s == 0;
  }

  virtual Status attach(Runnable* r) {
    Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
    t->thread = pthread_self();
    r->attach(t);
    return 0;
  }

  virtual Status start(Runnable* r) {
    Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
    r->attach(t);
    int rv UNUSED = pthread_create(&(t->thread), 0, run, r);
    expect(this, rv == 0);
    return 0;
  }

  virtual Status make(System::Mutex** m) {
    *m = new (allocate(this, sizeof(Mutex))) Mutex(this);
    return 0;
  }

  virtual Status make(System::Monitor** m) {
    *m = new (allocate(this, sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual Status make(System::Local** l) {
    *l = new (allocate(this, sizeof(Local))) Local(this);
    return 0;
  }

  virtual Status handleSegFault(SignalHandler* handler) {
    return registerHandler(handler, SegFaultSignalIndex);
  }

  virtual Status visit(System::Thread* st, System::Thread* sTarget,
                       ThreadVisitor* visitor)
  {
    assert(this, st != sTarget);

    Thread* t = static_cast<Thread*>(st);
    Thread* target = static_cast<Thread*>(sTarget);

    ACQUIRE_MONITOR(t, visitLock);

    while (threadVisitor) visitLock->wait(t, 0);

    threadVisitor = visitor;
    visitTarget = target;

    int rv = pthread_kill(target->thread, VisitSignal);
    expect(this, rv == 0);

    while (visitTarget) visitLock->wait(t, 0);

    threadVisitor = 0;

    return 0;
  }

  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size, unsigned returnType)
  {
    return dynamicCall(function, arguments, types, count, size, returnType);
  }

  virtual Status map(System::Region** region, const char* name) {
    Status status = 1;

    int fd = ::open(name, O_RDONLY);
    if (fd != -1) {
      struct stat s;
      int r = fstat(fd, &s);
      if (r != -1) {
        void* data = mmap(0, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
        if (data) {
          *region = new (allocate(this, sizeof(Region)))
            Region(this, static_cast<uint8_t*>(data), s.st_size);
          status = 0;
        }
      }
      close(fd);
    }
    
    return status;
  }

  virtual Status open(System::Directory** directory, const char* name) {
    Status status = 1;
    
    DIR* d = opendir(name);
    if (d) {
      *directory = new (allocate(this, sizeof(Directory))) Directory(this, d);
      status = 0;
    }
    
    return status;
  }

  virtual FileType identify(const char* name) {
    struct stat s;
    int r = stat(name, &s);
    if (r == 0) {
      if (S_ISREG(s.st_mode)) {
        return TypeFile;
      } else if (S_ISDIR(s.st_mode)) {
        return TypeDirectory;
      } else {
        return TypeUnknown;
      }
    } else {
      return TypeDoesNotExist;
    }
  }

  virtual Status load(System::Library** lib,
                      const char* name,
                      bool mapName)
  {
    void* p;
    bool alreadyAllocated = false;
    bool isMain = false;
    unsigned nameLength = (name ? strlen(name) : 0);
    if (mapName and name) {
      unsigned size = nameLength + 3 + sizeof(SO_SUFFIX);
      char buffer[size];
      snprintf(buffer, size, "lib%s" SO_SUFFIX, name);
      p = dlopen(buffer, RTLD_LAZY | RTLD_LOCAL);
    } else {
      if (!name) {
        pathOfExecutable(this, &name, &nameLength);
        alreadyAllocated = true;
        isMain = true;
      }
      p = dlopen(name, RTLD_LAZY | RTLD_LOCAL);
    }
 
    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", name, p);
      }

      char* n;
      if (name) {
        n = static_cast<char*>(allocate(this, nameLength + 1));
        memcpy(n, name, nameLength + 1);
        if (alreadyAllocated) {
          free(name);
        }
      } else {
        n = 0;
      }

      *lib = new (allocate(this, sizeof(Library)))
        Library(this, p, n, nameLength, mapName, isMain);

      return 0;
    } else {
//       fprintf(stderr, "dlerror: %s\n", dlerror());
      return 1;
    }
  }

  virtual char pathSeparator() {
    return ':';
  }

  virtual int64_t now() {
    timeval tv = { 0, 0 };
    gettimeofday(&tv, 0);
    return (static_cast<int64_t>(tv.tv_sec) * 1000) +
      (static_cast<int64_t>(tv.tv_usec) / 1000);
  }

  virtual void exit(int code) {
    ::exit(code);
  }

  virtual void abort() {
    ::abort();
  }

  virtual void dispose() {
    visitLock->dispose();

    registerHandler(0, InterruptSignalIndex);
    registerHandler(0, VisitSignalIndex);
    system = 0;

    ::free(this);
  }


  class NullSignalHandler: public SignalHandler {
    virtual bool handleSignal(void**, void**, void**, void**) { return false; }
  } nullHandler;

  SignalHandler* handlers[3];
  struct sigaction oldHandlers[3];

  ThreadVisitor* threadVisitor;
  Thread* visitTarget;
  System::Monitor* visitLock;
};

void
handleSignal(int signal, siginfo_t* info, void* context)
{
  ucontext_t* c = static_cast<ucontext_t*>(context);

#ifndef BASE_REGISTER
#  define BASE_REGISTER(x) 0
#endif

  void* ip = reinterpret_cast<void*>(IP_REGISTER(c));
  void* base = reinterpret_cast<void*>(BASE_REGISTER(c));
  void* stack = reinterpret_cast<void*>(STACK_REGISTER(c));
  void* thread = reinterpret_cast<void*>(THREAD_REGISTER(c));

  unsigned index;

  switch (signal) {
  case VisitSignal: {
    index = VisitSignalIndex;

    system->threadVisitor->visit(ip, base, stack);

    System::Thread* t = system->visitTarget;
    system->visitTarget = 0;

    ACQUIRE_MONITOR(t, system->visitLock);
    system->visitLock->notifyAll(t);
  } break;

  case SegFaultSignal: {
    index = SegFaultSignalIndex;

    bool jump = system->handlers[index]->handleSignal
      (&ip, &base, &stack, &thread);

    if (jump) {
      // I'd like to use setcontext here (and get rid of the
      // sigprocmask call), but it doesn't work on my Linux x86_64
      // system, and I can't tell from the documentation if it's even
      // supposed to work.

      sigset_t set;

      sigemptyset(&set);
      sigaddset(&set, SegFaultSignal);
      sigprocmask(SIG_UNBLOCK, &set, 0);

      vmJump(ip, base, stack, thread);
    }
  } break;

  case InterruptSignal: {
    index = InterruptSignalIndex;
  } break;

  default: abort();
  }

  if (system->oldHandlers[index].sa_flags & SA_SIGINFO) {
    system->oldHandlers[index].sa_sigaction(signal, info, context);
  } else if (system->oldHandlers[index].sa_handler) {
    system->oldHandlers[index].sa_handler(signal);
  } else {
    switch (signal) {
    case VisitSignal:
      break;

    default:
      abort();
    }
  }
}

} // namespace

namespace vm {

System*
makeSystem(const char*)
{
  return new (malloc(sizeof(MySystem))) MySystem();
}

} // namespace vm
