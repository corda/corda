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
    virtual bool tryAcquire(void* context) = 0;
    virtual void acquire(void* context) = 0;
    virtual void release(void* context) = 0;
    virtual void wait(void* context, int64_t time) = 0;
    virtual void notify(void* context) = 0;
    virtual void notifyAll(void* context) = 0;
    virtual void dispose() = 0;
  };

  class ReadWriteLock {
   public:
    ReadWriteLock(System* s): s(s), m(0), readers(0), writer(0) {
      if (not s->success(s->make(&m))) {
        s->abort();
      }
    }

    ~ReadWriteLock() {
      if (readers or writer) {
        s->abort();
      }

      m->dispose();
    }

    bool tryAcquireRead(void* context) {
      bool result;
      m->acquire(context);
      if (writer) {
        result = false;
      } else {
        result = true;
        ++ readers;
      }
      m->release(context);
      return result;
    }

    void acquireRead(void* context) {
      m->acquire(context);
      while (writer) {
        m->wait(context, 0);
      }
      ++ readers;
      m->release(context);
    }

    void releaseRead(void* context) {
      m->acquire(context);
      if (-- readers == 0) {
        m->notify(context);
      }
      m->release(context);
    }

    bool tryAcquireWrite(void* context) {
      bool result;
      m->acquire(context);
      if (readers or writer) {
        result = false;
      } else {
        result = true;
        writer = context;
      }
      m->release(context);
      return result;
    }

    void acquireWrite(void* context) {
      m->acquire(context);
      while (readers or writer) {
        m->wait(context, 0);
      }
      writer = context;
      m->release(context);
    }

    void releaseWrite(void* context) {
      if (writer != context) {
        s->abort();
      }

      m->acquire(context);
      writer = 0;
      m->notifyAll(context);
      m->release(context);
    }

   private:
    System* s;
    Monitor* m;
    unsigned readers;
    void* writer;
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
