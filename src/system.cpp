#include "sys/mman.h"
#include "sys/types.h"
#include "sys/stat.h"
#include "sys/time.h"
#include "time.h"
#include "fcntl.h"
#include "dlfcn.h"
#include "errno.h"
#include "pthread.h"
#include "signal.h"
#include "stdint.h"

#include "system.h"

#define ACQUIRE(x) MutexResource MAKE_NAME(mutexResource_) (x)


#ifdef __i386__

extern "C" uint64_t
cdeclCall(void* function, void* stack, unsigned stackSize,
          unsigned returnType);

namespace {

inline uint64_t
dynamicCall(void* function, uint32_t* arguments, uint8_t*,
            unsigned, unsigned argumentsSize, unsigned returnType)
{
  return cdeclCall(function, arguments, argumentsSize, returnType);
}

} // namespace

#elif defined __x86_64__

extern "C" uint64_t
amd64Call(void* function, void* stack, unsigned stackSize,
          void* gprTable, void* sseTable, unsigned returnType);

namespace {

uint64_t
dynamicCall(void* function, uint64_t* arguments, uint8_t* argumentTypes,
            unsigned argumentCount, unsigned, unsigned returnType)
{
  const unsigned GprCount = 6;
  uint64_t gprTable[GprCount];
  unsigned gprIndex = 0;

  const unsigned SseCount = 8;
  uint64_t sseTable[SseCount];
  unsigned sseIndex = 0;

  uint64_t stack[argumentCount];
  unsigned stackIndex = 0;

  for (unsigned i = 0; i < argumentCount; ++i) {
    switch (argumentTypes[i]) {
    case FLOAT_TYPE:
    case DOUBLE_TYPE: {
      if (sseIndex < SseCount) {
        sseTable[sseIndex++] = arguments[i];
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;

    default: {
      if (gprIndex < GprCount) {
        gprTable[gprIndex++] = arguments[i];
      } else {
        stack[stackIndex++] = arguments[i];
      }
    } break;
    }
  }

  return amd64Call(function, stack, stackIndex * 8, (gprIndex ? gprTable : 0),
                   (sseIndex ? sseTable : 0), returnType);
}

} // namespace

#else
#  error unsupported platform
#endif

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

const int InterruptSignal = SIGUSR2;

void
handleSignal(int)
{
  // ignore
}

void*
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
      int rv = pthread_join(thread, 0);
      assert(s, rv == 0);
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
        ACQUIRE(t->mutex);
      
        if (t->r->interrupted()) {
          t->r->setInterrupted(false);
          return true;
        }

        t->flags |= Waiting;

        append(t);

        unsigned depth = this->depth;
        this->depth = 0;
        owner_ = 0;
        pthread_mutex_unlock(&mutex);

        if (time) {
          int64_t then = s->now() + time;
          timespec ts = { then / 1000, (then % 1000) * 1000 * 1000 };
          int rv = pthread_cond_timedwait
            (&(t->condition), &(t->mutex), &ts);
          assert(s, rv == 0 or rv == ETIMEDOUT or rv == EINTR);
        } else {
          int rv = pthread_cond_wait(&(t->condition), &(t->mutex));
          assert(s, rv == 0 or rv == EINTR);
        }

        pthread_mutex_lock(&mutex);
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
      ACQUIRE(t->mutex);

      t->flags |= Notified;
      int rv = pthread_cond_signal(&(t->condition));
      assert(s, rv == 0);
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

  class Library: public System::Library {
   public:
    Library(System* s, void* p, const char* name, System::Library* next):
      s(s),
      p(p),
      name_(name),
      next_(next)
    { }

    virtual void* resolve(const char* function) {
      return dlsym(p, function);
    }

    virtual const char* name() {
      return name_;
    }

    virtual System::Library* next() {
      return next_;
    }

    virtual void dispose() {
      if (Verbose) {
        fprintf(stderr, "close %p\n", p);
      }

      dlclose(p);

      if (next_) {
        next_->dispose();
      }

      s->free(name_);
      s->free(this);
    }

    System* s;
    void* p;
    const char* name_;
    System::Library* next_;
  };

  MySystem(unsigned limit): limit(limit), count(0) {
    pthread_mutex_init(&mutex, 0);

    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&(sa.sa_mask));
    sa.sa_handler = handleSignal;
    
    int rv = sigaction(InterruptSignal, &sa, 0);
    assert(this, rv == 0);
  }

  virtual bool success(Status s) {
    return s == 0;
  }

  virtual void* tryAllocate(unsigned size) {
    pthread_mutex_lock(&mutex);

    if (Verbose) {
      fprintf(stderr, "try %d; count: %d; limit: %d\n",
              size, count, limit);
    }

    if (count + size > limit) {
      pthread_mutex_unlock(&mutex);
      return 0;
    } else {
      uintptr_t* up = static_cast<uintptr_t*>
        (malloc(size + sizeof(uintptr_t)));
      if (up == 0) {
        pthread_mutex_unlock(&mutex);
        sysAbort(this);
      } else {
        *up = size;
        count += *up;
      
        pthread_mutex_unlock(&mutex);
        return up + 1;
      }
    }
  }

  virtual void free(const void* p) {
    pthread_mutex_lock(&mutex);

    if (p) {
      const uintptr_t* up = static_cast<const uintptr_t*>(p) - 1;
      if (count < *up) {
        abort();
      }
      count -= *up;

      if (Verbose) {
        fprintf(stderr, "free " LD "; count: %d; limit: %d\n",
                *up, count, limit);
      }

      ::free(const_cast<uintptr_t*>(up));
    }

    pthread_mutex_unlock(&mutex);
  }

  virtual Status attach(Runnable* r) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, r);
    t->thread = pthread_self();
    r->attach(t);
    return 0;
  }

  virtual Status start(Runnable* r) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, r);
    r->attach(t);
    int rv = pthread_create(&(t->thread), 0, run, r);
    assert(this, rv == 0);
    return 0;
  }

  virtual Status make(System::Monitor** m) {
    *m = new (System::allocate(sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size, unsigned returnType)
  {
    return dynamicCall(function, arguments, types, count, size, returnType);
  }

  virtual Status load(System::Library** lib,
                      const char* name,
                      System::Library* next)
  {
    unsigned nameLength = strlen(name);
    unsigned size = nameLength + 7;
    char buffer[size];
    snprintf(buffer, size, "lib%s.so", name);
 
    void* p = dlopen(buffer, RTLD_LAZY);
    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", buffer, p);
      }

      char* n = static_cast<char*>(System::allocate(nameLength + 1));
      memcpy(n, name, nameLength + 1);
      *lib = new (System::allocate(sizeof(Library))) Library(this, p, n, next);
      return 0;
    } else {
      return 1;
    }
  }

  virtual void exit(int code) {
    ::exit(code);
  }

  int64_t now() {
    timeval tv = { 0, 0 };
    gettimeofday(&tv, 0);
    return (static_cast<int64_t>(tv.tv_sec) * 1000) +
      (static_cast<int64_t>(tv.tv_usec) / 1000);
  }

  virtual void abort() {
    ::abort();
  }

  virtual void dispose() {
    pthread_mutex_destroy(&mutex);
    ::free(this);
  }

  pthread_mutex_t mutex;
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
