#include "sys/mman.h"
#include "sys/types.h"
#include "sys/stat.h"
#include "sys/time.h"
#include "time.h"
#include "fcntl.h"
#include "dlfcn.h"
#include "errno.h"
#include "pthread.h"
#include "stdint.h"

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

#include "system.h"

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

void*
run(void* t)
{
  static_cast<System::Thread*>(t)->run();
  return 0;
}

int64_t
now()
{
  timeval tv = { 0, 0 };
  gettimeofday(&tv, 0);
  return (static_cast<int64_t>(tv.tv_sec) * 1000) +
    (static_cast<int64_t>(tv.tv_usec) / 1000);
}

const bool Verbose = false;

class MySystem: public System {
 public:
  class Thread: public System::Thread {
   public:
    Thread(System* s, System::Runnable* r): s(s), r(r) { }

    virtual void run() {
      r->run(this);
    }

    virtual void join() {
      int rv = pthread_join(thread, 0);
      assert(s, rv == 0);
    }

    virtual void dispose() {
      if (r) {
        r->dispose();
      }
      s->free(this);
    }

    System* s;
    System::Runnable* r;
    pthread_t thread;
  };

  class Monitor: public System::Monitor {
   public:
    Monitor(System* s): s(s), context(0), depth(0) {
      pthread_mutex_init(&mutex, 0);
      pthread_cond_init(&condition, 0);      
    }

    virtual bool tryAcquire(void* context) {
      if (this->context == context) {
        ++ depth;
        return true;
      } else {
        switch (pthread_mutex_trylock(&mutex)) {
        case EBUSY:
          return false;

        case 0:
          this->context = context;
          ++ depth;
          return true;

        default:
          sysAbort(s);
        }
      }
    }

    virtual void acquire(void* context) {
      if (this->context != context) {
        pthread_mutex_lock(&mutex);
        this->context = context;
      }
      ++ depth;
    }

    virtual void release(void* context) {
      if (this->context == context) {
        if (-- depth == 0) {
          this->context = 0;
          pthread_mutex_unlock(&mutex);
        }
      } else {
        sysAbort(s);
      }
    }

    virtual void wait(void* context, int64_t time) {
      if (this->context == context) {
        unsigned depth = this->depth;
        this->depth = 0;
        this->context = 0;
        if (time) {
          int64_t then = now() + time;
          timespec ts = { then / 1000, (then % 1000) * 1000 * 1000 };
          int rv = pthread_cond_timedwait(&condition, &mutex, &ts);
          assert(s, rv == 0);
        } else {
          int rv = pthread_cond_wait(&condition, &mutex);
          assert(s, rv == 0);
        }
        this->context = context;
        this->depth = depth;
      } else {
        sysAbort(s);
      }
    }

    virtual void notify(void* context) {
      if (this->context == context) {
        int rv = pthread_cond_signal(&condition);
        assert(s, rv == 0);
      } else {
        sysAbort(s);
      }
    }

    virtual void notifyAll(void* context) {
      if (this->context == context) {
        int rv = pthread_cond_broadcast(&condition);
        assert(s, rv == 0);
      } else {
        sysAbort(s);
      }
    }
    
    virtual void* owner() {
      return context;
    }

    virtual void dispose() {
      assert(s, context == 0);
      pthread_mutex_destroy(&mutex);
      pthread_cond_destroy(&condition);
      s->free(this);
    }

    System* s;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    void* context;
    unsigned depth;
  };

  class Library: public System::Library {
   public:
    Library(System* s, void* p, System::Library* next):
      s(s),
      p(p),
      next_(next)
    { }

    virtual void* resolve(const char* function) {
      return dlsym(p, function);
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
      s->free(this);
    }

    System* s;
    void* p;
    System::Library* next_;
  };

  MySystem(unsigned limit): limit(limit), count(0) {
    pthread_mutex_init(&mutex, 0);
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

  virtual Status attach(System::Thread** tp) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, 0);
    t->thread = pthread_self();
    *tp = t;
    return 0;
  }

  virtual Status start(Runnable* r) {
    Thread* t = new (System::allocate(sizeof(Thread))) Thread(this, r);
    int rv = pthread_create(&(t->thread), 0, run, t);
    assert(this, rv == 0);
    return 0;
  }

  virtual Status make(System::Monitor** m) {
    *m = new (System::allocate(sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual void sleep(int64_t milliseconds) {
    timespec ts = { milliseconds / 1000, (milliseconds % 1000) * 1000 * 1000 };

    nanosleep(&ts, 0);
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
    unsigned size = strlen(name) + 7;
    char buffer[size];
    snprintf(buffer, size, "lib%s.so", name);
 
    void* p = dlopen(buffer, RTLD_LAZY);
    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", buffer, p);
      }

      *lib = new (System::allocate(sizeof(Library))) Library(this, p, next);
      return 0;
    } else {
      return 1;
    }
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
