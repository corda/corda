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

#include "x86.h"
#include "system.h"

#define ACQUIRE(x) MutexResource MAKE_NAME(mutexResource_) (x)

using namespace vm;

namespace {

System::SignalHandler* segFaultHandler = 0;
struct sigaction oldSegFaultHandler;

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

const int InterruptSignal = SIGUSR2;
#ifdef __APPLE__
const int SegFaultSignal = SIGBUS;
#else
const int SegFaultSignal = SIGSEGV;
#endif

#ifdef __x86_64__
#  define IP_REGISTER(context) (context->uc_mcontext.gregs[REG_RIP])
#  define BASE_REGISTER(context) (context->uc_mcontext.gregs[REG_RBP])
#  define STACK_REGISTER(context) (context->uc_mcontext.gregs[REG_RSP])
#  define THREAD_REGISTER(context) (context->uc_mcontext.gregs[REG_RBX])
#elif defined __APPLE__
#  define IP_REGISTER(context) (context->uc_mcontext->__ss.__eip)
#  define BASE_REGISTER(context) (context->uc_mcontext->__ss.__ebp)
#  define STACK_REGISTER(context) (context->uc_mcontext->__ss.__esp)
#  define THREAD_REGISTER(context) (context->uc_mcontext->__ss.__ebx)
#elif defined __i386__
#  define IP_REGISTER(context) (context->uc_mcontext.gregs[REG_EIP])
#  define BASE_REGISTER(context) (context->uc_mcontext.gregs[REG_EBP])
#  define STACK_REGISTER(context) (context->uc_mcontext.gregs[REG_ESP])
#  define THREAD_REGISTER(context) (context->uc_mcontext.gregs[REG_EBX])
#else
#  error unsupported architecture
#endif

void
handleSignal(int signal, siginfo_t* info, void* context)
{
  if (signal == SegFaultSignal) {
    ucontext_t* c = static_cast<ucontext_t*>(context);

    void* ip = reinterpret_cast<void*>(IP_REGISTER(c));
    void* base = reinterpret_cast<void*>(BASE_REGISTER(c));
    void* stack = reinterpret_cast<void*>(STACK_REGISTER(c));
    void* thread = reinterpret_cast<void*>(THREAD_REGISTER(c));

    bool jump = segFaultHandler->handleSignal
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
    } else if (oldSegFaultHandler.sa_flags & SA_SIGINFO) {
      oldSegFaultHandler.sa_sigaction(signal, info, context);
    } else if (oldSegFaultHandler.sa_handler) {
      oldSegFaultHandler.sa_handler(signal);
    } else {
      abort();
    }
  }
}

void*
run(void* r)
{
  static_cast<System::Runnable*>(r)->run();
  return 0;
}

void*
allocate(System* s, unsigned size)
{
  void* p = s->tryAllocate(size, false);
  if (p == 0) abort();
  return p;
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
      s->free(this, sizeof(*this), false);
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
      s->free(this, sizeof(*this), false);
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
        bool interrupted;
        bool notified;
        unsigned depth;

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
      s->free(this, sizeof(*this), false);
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

      s->free(this, sizeof(*this), false);
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
      s->free(this, sizeof(*this), false);
    }

    System* s;
    uint8_t* start_;
    size_t length_;
  };

  class Library: public System::Library {
   public:
    Library(System* s, void* p, const char* name, unsigned nameLength,
            bool mapName):
      s(s),
      p(p),
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

      dlclose(p);

      if (next_) {
        next_->disposeAll();
      }

      if (name_) {
        s->free(name_, nameLength + 1, false);
      }

      s->free(this, sizeof(*this), false);
    }

    System* s;
    void* p;
    const char* name_;
    unsigned nameLength;
    bool mapName_;
    System::Library* next_;
  };

  MySystem() {
    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&(sa.sa_mask));
    sa.sa_flags = SA_SIGINFO;
    sa.sa_sigaction = handleSignal;
    
    int rv UNUSED = sigaction(InterruptSignal, &sa, 0);
    expect(this, rv == 0);
  }

  virtual void* tryAllocate(unsigned size, bool executable) {
    assert(this, (not executable) or (size % LikelyPageSizeInBytes == 0));

#ifndef MAP_32BIT
#define MAP_32BIT 0
#endif

    if (executable) {
      void* p = mmap(0, size, PROT_EXEC | PROT_READ | PROT_WRITE,
                     MAP_PRIVATE | MAP_ANON | MAP_32BIT, -1, 0);

      if (p == MAP_FAILED) {
        return 0;
      } else {
        return p;
      }
    } else {
      return malloc(size);
    }
  }

  virtual void free(const void* p, unsigned size, bool executable) {
    if (p) {
      if (executable) {
        int r UNUSED = munmap(const_cast<void*>(p), size);
        assert(this, r == 0);
      } else {
        ::free(const_cast<void*>(p));
      }
    }
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
    if (handler) {
      segFaultHandler = handler;

      struct sigaction sa;
      memset(&sa, 0, sizeof(struct sigaction));
      sigemptyset(&(sa.sa_mask));
      sa.sa_flags = SA_SIGINFO;
      sa.sa_sigaction = handleSignal;
    
      return sigaction(SegFaultSignal, &sa, &oldSegFaultHandler);
    } else if (segFaultHandler) {
      segFaultHandler = 0;
      return sigaction(SegFaultSignal, &oldSegFaultHandler, 0);
    } else {
      return 1;
    }
  }

  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size, unsigned returnType)
  {
    return dynamicCall(function, arguments, types, count, size, returnType);
  }

  virtual Status map(System::Region** region, const char* name) {
    Status status = 1;

    int fd = open(name, O_RDONLY);
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

  virtual FileType identify(const char* name) {
    struct stat s;
    int r = stat(name, &s);
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
                      bool mapName)
  {
    void* p;
    unsigned nameLength = (name ? strlen(name) : 0);
    if (mapName) {
      unsigned size = nameLength + 3 + sizeof(SO_SUFFIX);
      char buffer[size];
      snprintf(buffer, size, "lib%s" SO_SUFFIX, name);
      p = dlopen(buffer, RTLD_LAZY);
    } else {
      p = dlopen(name, RTLD_LAZY);
    }
 
    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", name, p);
      }

      char* n;
      if (name) {
        n = static_cast<char*>(allocate(this, nameLength + 1));
        memcpy(n, name, nameLength + 1);
      } else {
        n = 0;
      }

      *lib = new (allocate(this, sizeof(Library)))
        Library(this, p, n, nameLength, mapName);

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
    ::free(this);
  }
};

} // namespace

namespace vm {

System*
makeSystem()
{
  return new (malloc(sizeof(MySystem))) MySystem();
}

} // namespace vm
