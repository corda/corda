/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef __STDC_CONSTANT_MACROS
#define __STDC_CONSTANT_MACROS
#endif

#include "sys/types.h"
#ifdef __APPLE__
#include "CoreFoundation/CoreFoundation.h"
#include "sys/ucontext.h"
#undef assert
#elif defined(__ANDROID__)
#include <asm/sigcontext.h> /* for sigcontext */
#include <asm/signal.h>     /* for stack_t */
typedef struct ucontext {
  unsigned long uc_flags;
  struct ucontext* uc_link;
  stack_t uc_stack;
  struct sigcontext uc_mcontext;
  unsigned long uc_sigmask;
} ucontext_t;
#else
#if defined __FreeBSD__
#include "limits.h"
#endif
#include "ucontext.h"
#endif

#include "sys/mman.h"

#include "sys/stat.h"
#include "sys/time.h"
#include "time.h"
#include "fcntl.h"
#include "dlfcn.h"
#include "errno.h"
#include "unistd.h"
#include "pthread.h"
#include "signal.h"
#include "stdint.h"
#include "dirent.h"
#include "sched.h"

#include <avian/arch.h>
#include <avian/append.h>

#include <avian/system/system.h>
#include <avian/system/signal.h>
#include <avian/system/memory.h>
#include <avian/util/math.h>

#define ACQUIRE(x) MutexResource MAKE_NAME(mutexResource_)(x)

using namespace vm;
using namespace avian::util;

namespace {

class MutexResource {
 public:
  MutexResource(pthread_mutex_t& m) : m(&m)
  {
    pthread_mutex_lock(&m);
  }

  ~MutexResource()
  {
    pthread_mutex_unlock(m);
  }

 private:
  pthread_mutex_t* m;
};

const int VisitSignal = SIGUSR1;
const unsigned VisitSignalIndex = 0;
const int InterruptSignal = SIGUSR2;
const unsigned InterruptSignalIndex = 1;
const int PipeSignal = SIGPIPE;
const unsigned PipeSignalIndex = 2;

const int signals[] = {VisitSignal, InterruptSignal, PipeSignal};

const unsigned SignalCount = 3;

class MySystem;
MySystem* globalSystem;

void handleSignal(int signal, siginfo_t* info, void* context);

void* run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
  return 0;
}

void pathOfExecutable(System* s, const char** retBuf, unsigned* size)
{
#ifdef __APPLE__
  CFBundleRef bundle = CFBundleGetMainBundle();
  CFURLRef url = CFBundleCopyExecutableURL(bundle);
  CFStringRef path = CFURLCopyPath(url);
  path = CFURLCreateStringByReplacingPercentEscapes(
      kCFAllocatorDefault, path, CFSTR(""));
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

const unsigned Notified = 1 << 0;

class MySystem : public System {
 public:
  class Thread : public System::Thread {
   public:
    Thread(System* s, System::Runnable* r) : s(s), r(r), next(0), flags(0)
    {
      pthread_mutex_init(&mutex, 0);
      pthread_cond_init(&condition, 0);
    }

    virtual void interrupt()
    {
      ACQUIRE(mutex);

      r->setInterrupted(true);

      pthread_kill(thread, InterruptSignal);

      // pthread_kill won't necessarily wake a thread blocked in
      // pthread_cond_{timed}wait (it does on Linux but not Mac OS),
      // so we signal the condition as well:
      int rv UNUSED = pthread_cond_signal(&condition);
      expect(s, rv == 0);
    }

    virtual bool getAndClearInterrupted()
    {
      ACQUIRE(mutex);

      bool interrupted = r->interrupted();

      r->setInterrupted(false);

      return interrupted;
    }

    virtual void join()
    {
      int rv UNUSED = pthread_join(thread, 0);
      expect(s, rv == 0);
    }

    virtual void dispose()
    {
      pthread_mutex_destroy(&mutex);
      pthread_cond_destroy(&condition);
      ::free(this);
    }

    pthread_t thread;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    System* s;
    System::Runnable* r;
    Thread* next;
    unsigned flags;
  };

  class Mutex : public System::Mutex {
   public:
    Mutex(System* s) : s(s)
    {
      pthread_mutex_init(&mutex, 0);
    }

    virtual void acquire()
    {
      pthread_mutex_lock(&mutex);
    }

    virtual void release()
    {
      pthread_mutex_unlock(&mutex);
    }

    virtual void dispose()
    {
      pthread_mutex_destroy(&mutex);
      ::free(this);
    }

    System* s;
    pthread_mutex_t mutex;
  };

  class Monitor : public System::Monitor {
   public:
    Monitor(System* s) : s(s), owner_(0), first(0), last(0), depth(0)
    {
      pthread_mutex_init(&mutex, 0);
    }

    virtual bool tryAcquire(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        ++depth;
        return true;
      } else {
        switch (pthread_mutex_trylock(&mutex)) {
        case EBUSY:
          return false;

        case 0:
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

      if (owner_ != t) {
        pthread_mutex_lock(&mutex);
        owner_ = t;
      }
      ++depth;
    }

    virtual void release(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);

      if (owner_ == t) {
        if (--depth == 0) {
          owner_ = 0;
          pthread_mutex_unlock(&mutex);
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
        expect(s, t != last);
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
            expect(s, previous != t->next);
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

      if (owner_ == t) {
        // Initialized here to make gcc 4.2 a happy compiler
        bool interrupted = false;
        bool notified = false;
        unsigned depth = 0;

        {
          ACQUIRE(t->mutex);

          expect(s, (t->flags & Notified) == 0);

          interrupted = t->r->interrupted();
          if (interrupted and clearInterrupted) {
            t->r->setInterrupted(false);
          }

          append(t);

          depth = this->depth;
          this->depth = 0;
          owner_ = 0;
          pthread_mutex_unlock(&mutex);

          if (not interrupted) {
            // pretend anything greater than one million years (in
            // milliseconds) is infinity so as to avoid overflow:
            if (time and time < INT64_C(31536000000000000)) {
              int64_t then = s->now() + time;
              timespec ts = {static_cast<long>(then / 1000),
                             static_cast<long>((then % 1000) * 1000 * 1000)};
              int rv UNUSED
                  = pthread_cond_timedwait(&(t->condition), &(t->mutex), &ts);
              expect(s, rv == 0 or rv == ETIMEDOUT or rv == EINTR);
            } else {
              int rv UNUSED = pthread_cond_wait(&(t->condition), &(t->mutex));
              expect(s, rv == 0 or rv == EINTR);
            }

            interrupted = t->r->interrupted();
            if (interrupted and clearInterrupted) {
              t->r->setInterrupted(false);
            }
          }

          notified = ((t->flags & Notified) != 0);
        }

        pthread_mutex_lock(&mutex);

        {
          ACQUIRE(t->mutex);
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
      ACQUIRE(t->mutex);

      t->flags |= Notified;
      int rv UNUSED = pthread_cond_signal(&(t->condition));
      expect(s, rv == 0);
    }

    virtual void notify(System::Thread* context)
    {
      Thread* t = static_cast<Thread*>(context);

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
      expect(s, owner_ == 0);
      pthread_mutex_destroy(&mutex);
      ::free(this);
    }

    System* s;
    pthread_mutex_t mutex;
    Thread* owner_;
    Thread* first;
    Thread* last;
    unsigned depth;
  };

  class Local : public System::Local {
   public:
    Local(System* s) : s(s)
    {
      int r UNUSED = pthread_key_create(&key, 0);
      expect(s, r == 0);
    }

    virtual void* get()
    {
      return pthread_getspecific(key);
    }

    virtual void set(void* p)
    {
      int r UNUSED = pthread_setspecific(key, p);
      expect(s, r == 0);
    }

    virtual void dispose()
    {
      int r UNUSED = pthread_key_delete(key);
      expect(s, r == 0);

      ::free(this);
    }

    System* s;
    pthread_key_t key;
  };

  class Region : public System::Region {
   public:
    Region(System* s, uint8_t* start, size_t length)
        : s(s), start_(start), length_(length)
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
        munmap(start_, length_);
      }
      ::free(this);
    }

    System* s;
    uint8_t* start_;
    size_t length_;
  };

  class Directory : public System::Directory {
   public:
    Directory(System* s, DIR* directory) : s(s), directory(directory)
    {
    }

    virtual const char* next()
    {
      if (directory) {
        dirent* e = readdir(directory);
        if (e) {
          return e->d_name;
        }
      }
      return 0;
    }

    virtual void dispose()
    {
      if (directory) {
        closedir(directory);
      }
      ::free(this);
    }

    System* s;
    DIR* directory;
  };

  class Library : public System::Library {
   public:
    Library(System* s,
            void* p,
            const char* name,
            unsigned nameLength,
            bool isMain)
        : s(s),
          p(p),
          mainExecutable(isMain),
          name_(name),
          nameLength(nameLength),
          next_(0)
    {
    }

    virtual void* resolve(const char* function)
    {
      return dlsym(p, function);
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
        fprintf(stderr, "close %p\n", p);
      }

      if (not mainExecutable)
        dlclose(p);

      if (next_) {
        next_->disposeAll();
      }

      if (name_) {
        ::free(const_cast<char*>(name_));
      }

      ::free(this);
    }

    System* s;
    void* p;
    bool mainExecutable;
    const char* name_;
    unsigned nameLength;
    System::Library* next_;
  };

  MySystem(bool reentrant) : reentrant(reentrant), threadVisitor(0), visitTarget(0)
  {
    if (not reentrant) {
      expect(this, globalSystem == 0);
      globalSystem = this;

      expect(this, registerHandler(InterruptSignalIndex));
      expect(this, registerHandler(VisitSignalIndex));
      expect(this, registerHandler(PipeSignalIndex));
    
      expect(this, make(&visitLock) == 0);
    }
  }

  // Returns true on success, false on failure
  bool unregisterHandler(int index)
  {
    return sigaction(signals[index], oldHandlers + index, 0) == 0;
  }

  // Returns true on success, false on failure
  bool registerHandler(int index)
  {
    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&(sa.sa_mask));
    sa.sa_flags = SA_SIGINFO;
    sa.sa_sigaction = handleSignal;

    return sigaction(signals[index], &sa, oldHandlers + index) == 0;
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
    t->thread = pthread_self();
    r->attach(t);
    return 0;
  }

  virtual Status start(Runnable* r)
  {
    Thread* t = new (allocate(this, sizeof(Thread))) Thread(this, r);
    r->attach(t);
    int rv UNUSED = pthread_create(&(t->thread), 0, run, r);
    expect(this, rv == 0);
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
    expect(this, not reentrant);
    
    assertT(this, st != sTarget);

    Thread* target = static_cast<Thread*>(sTarget);

#ifdef __APPLE__
    // On Mac OS, signals sent using pthread_kill are never delivered
    // if the target thread is blocked (e.g. acquiring a lock or
    // waiting on a condition), so we can't rely on it and must use
    // the Mach-specific thread execution API instead.

    mach_port_t port = pthread_mach_thread_np(target->thread);

    if (thread_suspend(port))
      return -1;

    THREAD_STATE_TYPE state;
    mach_msg_type_number_t stateCount = THREAD_STATE_COUNT;
    kern_return_t rv
        = thread_get_state(port,
                           THREAD_STATE,
                           reinterpret_cast<thread_state_t>(&state),
                           &stateCount);

    if (rv == 0) {
      visitor->visit(reinterpret_cast<void*>(THREAD_STATE_IP(state)),
                     reinterpret_cast<void*>(THREAD_STATE_STACK(state)),
                     reinterpret_cast<void*>(THREAD_STATE_LINK(state)));
    }

    thread_resume(port);

    return rv ? -1 : 0;
#else   // not __APPLE__
    Thread* t = static_cast<Thread*>(st);

    ACQUIRE_MONITOR(t, visitLock);

    while (threadVisitor)
      visitLock->wait(t, 0);

    threadVisitor = visitor;
    visitTarget = target;

    int rv = pthread_kill(target->thread, VisitSignal);

    int result;
    if (rv == 0) {
      while (visitTarget)
        visitLock->wait(t, 0);

      result = 0;
    } else {
      visitTarget = 0;

      result = -1;
    }

    threadVisitor = 0;

    globalSystem->visitLock->notifyAll(t);

    return result;
#endif  // not  __APPLE__
  }

  virtual Status map(System::Region** region, const char* name)
  {
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

  virtual Status open(System::Directory** directory, const char* name)
  {
    Status status = 1;

    DIR* d = opendir(name);
    if (d) {
      *directory = new (allocate(this, sizeof(Directory))) Directory(this, d);
      status = 0;
    }

    return status;
  }

  virtual FileType stat(const char* name, size_t* length)
  {
#ifdef __FreeBSD__
    // Now the hack below causes the error "Dereferencing type-punned
    // pointer will break strict aliasing rules", so another workaround
    // is needed...
    struct stat ss;
    struct stat* s = &ss;
#else
    // Ugly Hack Alert: It seems that the Apple iOS Simulator's stat
    // implementation writes beyond the end of the struct stat we pass
    // it, which can clobber unrelated parts of the stack.  Perhaps
    // this is due to some kind of header/library mismatch, but I've
    // been unable to track it down so far.  The workaround is to give
    // it 8 words more than it should need, where 8 is a number I just
    // made up and seems to work.
    void* array[ceilingDivide(sizeof(struct stat), sizeof(void*)) + 8];
    struct stat* s = reinterpret_cast<struct stat*>(array);
#endif

    int r = ::stat(name, s);
    if (r == 0) {
      if (S_ISREG(s->st_mode)) {
        *length = s->st_size;
        return TypeFile;
      } else if (S_ISDIR(s->st_mode)) {
        *length = 0;
        return TypeDirectory;
      } else {
        *length = 0;
        return TypeUnknown;
      }
    } else {
      *length = 0;
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

  virtual const char* toAbsolutePath(AllocOnly* allocator, const char* name)
  {
    if (name[0] == '/') {
      return copy(allocator, name);
    } else {
      char buffer[PATH_MAX];
      return append(allocator, getcwd(buffer, PATH_MAX), "/", name);
    }
  }

  virtual Status load(System::Library** lib, const char* name)
  {
    unsigned nameLength = (name ? strlen(name) : 0);
    bool isMain = name == 0;
    if (isMain) {
      pathOfExecutable(this, &name, &nameLength);
    }
    void* p = dlopen(name, RTLD_LAZY | RTLD_LOCAL);

    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", name, p);
      }

      char* n;
      if (name) {
        n = static_cast<char*>(allocate(this, nameLength + 1));
        memcpy(n, name, nameLength + 1);
        if (isMain) {
          free(name);
        }
      } else {
        n = 0;
      }

      *lib = new (allocate(this, sizeof(Library)))
          Library(this, p, n, nameLength, isMain);

      return 0;
    } else {
      if (Verbose) {
        fprintf(stderr, "dlerror opening %s: %s\n", name, dlerror());
      }
      return 1;
    }
  }

  virtual char pathSeparator()
  {
    return ':';
  }

  virtual char fileSeparator()
  {
    return '/';
  }

  virtual int64_t now()
  {
    timeval tv = {0, 0};
    gettimeofday(&tv, 0);
    return (static_cast<int64_t>(tv.tv_sec) * 1000)
           + (static_cast<int64_t>(tv.tv_usec) / 1000);
  }

  virtual void yield()
  {
    sched_yield();
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
      visitLock->dispose();

      expect(this, unregisterHandler(InterruptSignalIndex));
      expect(this, unregisterHandler(VisitSignalIndex));
      expect(this, unregisterHandler(PipeSignalIndex));
      globalSystem = 0;
    }

    ::free(this);
  }

  struct sigaction oldHandlers[SignalCount];

  bool reentrant;
  ThreadVisitor* threadVisitor;
  Thread* visitTarget;
  System::Monitor* visitLock;
};

void handleSignal(int signal, siginfo_t*, void* context)
{
  ucontext_t* c = static_cast<ucontext_t*>(context);

  void* ip = reinterpret_cast<void*>(IP_REGISTER(c));
  void* stack = reinterpret_cast<void*>(STACK_REGISTER(c));
  void* link = reinterpret_cast<void*>(LINK_REGISTER(c));

  switch (signal) {
  case VisitSignal: {
    globalSystem->threadVisitor->visit(ip, stack, link);

    System::Thread* t = globalSystem->visitTarget;
    globalSystem->visitTarget = 0;

    ACQUIRE_MONITOR(t, globalSystem->visitLock);
    globalSystem->visitLock->notifyAll(t);
  } break;

  case InterruptSignal:
  case PipeSignal:
    break;

  default:
    abort();
  }
}

}  // namespace

namespace vm {

AVIAN_EXPORT System* makeSystem(bool reentrant)
{
  return new (malloc(sizeof(MySystem))) MySystem(reentrant);
}

}  // namespace vm
