#ifndef SYSTEM_H
#define SYSTEM_H

#include "common.h"

namespace vm {

class System {
 public:
  typedef intptr_t Status;

  class Thread {
   public:
    virtual ~Thread() { }
    virtual void run() = 0;
  };

  class Monitor {
   public:
    virtual ~Monitor() { }
    virtual bool tryAcquire(void* id) = 0;
    virtual void acquire(void* id) = 0;
    virtual void release(void* id) = 0;
    virtual void wait(void* id) = 0;
    virtual void notify(void* id) = 0;
    virtual void notifyAll(void* id) = 0;
    virtual void dispose() = 0;
  };

  class Library {
   public:
    virtual ~Library() { }
    virtual void* resolve(const char* function) = 0;
    virtual Library* next() = 0;
    virtual void dispose() = 0;
  };

  virtual ~System() { }

  virtual bool success(Status) = 0;
  virtual void* tryAllocate(unsigned size) = 0;
  virtual void free(const void*) = 0;
  virtual Status start(Thread*) = 0;
  virtual Status make(Monitor**) = 0;
  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size,
                        unsigned returnType) = 0;
  virtual Status load(Library**, const char* name, Library* next) = 0;
  virtual void abort() = 0;

  void* allocate(unsigned size) {
    void* p = tryAllocate(size);
    if (p == 0) {
      abort();
    }
    return p;
  }
};

inline void NO_RETURN
abort(System* s)
{
  s->abort(); // this should not return
  ::abort();
}

inline void
expect(System* s, bool v)
{
  if (UNLIKELY(not v)) abort(s);
}

#ifdef NDEBUG
inline void
assert(System*, bool)
{ }
#else
inline void
assert(System* s, bool v)
{
  expect(s, v);
}
#endif

} // namespace vm

#endif//SYSTEM_H
