#include "sys/mman.h"
#include "sys/types.h"
#include "sys/stat.h"
#include <sys/time.h>
#include <time.h>
#include "fcntl.h"
#include "dlfcn.h"
#include "errno.h"
#include "pthread.h"
#include "common.h"
#include "system.h"
#include "heap.h"
#include "class-finder.h"
#include "vm.h"

using namespace vm;

#ifdef __i386__
#define LD "%d"

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
#define LD "%ld"

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

namespace {

void*
run(void* t)
{
  static_cast<vm::System::Thread*>(t)->run();
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

const char*
append(vm::System* s, const char* a, const char* b, const char* c,
       const char* d)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  unsigned cl = strlen(c);
  unsigned dl = strlen(d);
  char* p = static_cast<char*>(s->allocate(al + bl + cl + dl + 1));
  memcpy(p, a, al);
  memcpy(p + al, b, bl);
  memcpy(p + al + bl, c, cl);
  memcpy(p + al + bl + cl, d, dl + 1);
  return p;
}

const bool Verbose = false;

class System: public vm::System {
 public:
  class Monitor: public vm::System::Monitor {
   public:
    Monitor(vm::System* s): s(s), context(0), depth(0) {
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
          vm::abort(s);
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
        vm::abort(s);
      }
    }

    virtual void wait(void* context, int64_t time) {
      if (this->context == context) {
        if (time) {
          int64_t then = now() + time;
          timespec ts = { then / 1000, (then % 1000) * 1000 * 1000 };
          int rv = pthread_cond_timedwait(&condition, &mutex, &ts);
          assert(s, rv == 0);
        } else {
          int rv = pthread_cond_wait(&condition, &mutex);
          assert(s, rv == 0);
        }
      } else {
        vm::abort(s);
      }
    }

    virtual void notify(void* context) {
      if (this->context == context) {
        int rv = pthread_cond_signal(&condition);
        assert(s, rv == 0);
      } else {
        vm::abort(s);
      }
    }

    virtual void notifyAll(void* context) {
      if (this->context == context) {
        int rv = pthread_cond_broadcast(&condition);
        assert(s, rv == 0);
      } else {
        vm::abort(s);
      }
    }

    virtual void dispose() {
      pthread_mutex_destroy(&mutex);
      pthread_cond_destroy(&condition);
      s->free(this);
    }

    vm::System* s;
    pthread_mutex_t mutex;
    pthread_cond_t condition;
    void* context;
    unsigned depth;
  };

  class Library: public vm::System::Library {
   public:
    Library(vm::System* s, void* p, vm::System::Library* next):
      s(s),
      p(p),
      next_(next)
    { }

    virtual void* resolve(const char* function) {
      return dlsym(p, function);
    }

    virtual vm::System::Library* next() {
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

    vm::System* s;
    void* p;
    vm::System::Library* next_;
  };

  System(unsigned limit): limit(limit), count(0) {
    pthread_mutex_init(&mutex, 0);
  }

  ~System() {
    pthread_mutex_destroy(&mutex);
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
        vm::abort(this);
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

  virtual Status start(Thread* t) {
    pthread_t thread;
    int rv = pthread_create(&thread, 0, run, t);
    assert(this, rv == 0);
    return 0;
  }

  virtual Status make(vm::System::Monitor** m) {
    *m = new (vm::System::allocate(sizeof(Monitor))) Monitor(this);
    return 0;
  }

  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size, unsigned returnType)
  {
    return dynamicCall(function, arguments, types, count, size, returnType);
  }

  virtual Status load(vm::System::Library** lib,
                      const char* name,
                      vm::System::Library* next)
  {
    unsigned size = strlen(name) + 7;
    char buffer[size];
    snprintf(buffer, size, "lib%s.so", name);
 
    void* p = dlopen(buffer, RTLD_LAZY);
    if (p) {
      if (Verbose) {
        fprintf(stderr, "open %s as %p\n", buffer, p);
      }

      *lib = new (vm::System::allocate(sizeof(Library)))
        Library(this, p, next);
      return 0;
    } else {
      return 1;
    }
  }

  virtual void abort() {
    ::abort();
  }

  pthread_mutex_t mutex;
  unsigned limit;
  unsigned count;
};

class ClassFinder: public vm::ClassFinder {
 public:
  ClassFinder(vm::System* system, const char** path):
    system(system),
    path(path)
  { }

  class Data: public vm::ClassFinder::Data {
   public:
    Data(vm::System* system, uint8_t* start, size_t length):
      system(system),
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
      system->free(this);
    }

    vm::System* system;
    uint8_t* start_;
    size_t length_;
  };

  virtual Data* find(const char* className) {
    Data* d = new (system->allocate(sizeof(Data))) Data(system, 0, 0);

    for (const char** p = path; *p; ++p) {
      const char* file = append(system, *p, "/", className, ".class");
      int fd = open(file, O_RDONLY);
      system->free(file);

      if (fd != -1) {
        struct stat s;
        int r = fstat(fd, &s);
        if (r != -1) {
          void* data = mmap(0, s.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
          if (data) {
            d->start_ = static_cast<uint8_t*>(data);
            d->length_ = s.st_size;
            return d;
          }
        }
      }
    }
    
    system->free(d);
    return 0;
  }

  vm::System* system;
  const char** path;
};

const char**
parsePath(vm::System* s, const char* path)
{
  class Tokenizer {
   public:
    class Token {
     public:
      Token(const char* s, unsigned length): s(s), length(length) { }

      const char* s;
      unsigned length;
    };

    Tokenizer(const char* s, char delimiter): s(s), delimiter(delimiter) { }

    bool hasMore() {
      while (*s == delimiter) ++s;
      return *s;
    }

    Token next() {
      const char* p = s;
      while (*s and *s != delimiter) ++s;
      return Token(p, s - p);
    }

    const char* s;
    char delimiter;
  };

  unsigned count = 0;
  for (Tokenizer t(path, ':'); t.hasMore(); t.next()) ++ count;

  const char** v = static_cast<const char**>
    (s->allocate((count + 1) * sizeof(const char*)));

  unsigned i = 0;
  for (Tokenizer t(path, ':'); t.hasMore(); ++i) {
    Tokenizer::Token token(t.next());
    char* p = static_cast<char*>(s->allocate(token.length + 1));
    memcpy(p, token.s, token.length);
    p[token.length] = 0;
    v[i] = p;
  }

  v[i] = 0;

  return v;
}

void
run(unsigned heapSize, const char* path, const char* class_, int argc,
    const char** argv)
{
  System s(heapSize);

  const char** pathv = parsePath(&s, path);
  ClassFinder cf(&s, pathv);

  Heap* heap = makeHeap(&s);

  run(&s, heap, &cf, class_, argc, argv);

  heap->dispose();

  for (const char** p = pathv; *p; ++p) {
    s.free(*p);
  }

  s.free(pathv);
}

void
usageAndExit(const char* name)
{
  fprintf(stderr, "usage: %s [-cp <classpath>] [-hs <maximum heap size>] "
          "<class name> [<argument> ...]\n", name);
  exit(-1);
}

} // namespace

int
main(int ac, const char** av)
{
  unsigned heapSize = 4 * 1024 * 1024;
  const char* path = ".";
  const char* class_ = 0;
  int argc = 0;
  const char** argv = 0;

  for (int i = 1; i < ac; ++i) {
    if (strcmp(av[i], "-cp") == 0) {
      path = av[++i];
    } else if (strcmp(av[i], "-hs") == 0) {
      heapSize = atoi(av[++i]);
    } else {
      class_ = av[i++];
      if (i < ac) {
        argc = ac - i;
        argv = av + i;
        i = ac;
      }
    }
  }

  if (class_ == 0) {
    usageAndExit(av[0]);
  }

  run(heapSize, path, class_, argc, argv);

  return 0;
}
