#ifndef SYSTEM_H
#define SYSTEM_H

#include "common.h"

namespace vm {

class System {
 public:
  typedef intptr_t Status;

  enum FileType {
    Unknown,
    DoesNotExist,
    File,
    Directory
  };

  class Thread {
   public:
    virtual ~Thread() { }
    virtual void interrupt() = 0;
    virtual void join() = 0;
    virtual void dispose() = 0;
  };

  class Runnable {
   public:
    virtual ~Runnable() { }
    virtual void attach(Thread*) = 0;
    virtual void run() = 0;
    virtual bool interrupted() = 0;
    virtual void setInterrupted(bool v) = 0;
  };

  class Mutex {
   public:
    virtual ~Mutex() { }
    virtual void acquire() = 0;
    virtual void release() = 0;
    virtual void dispose() = 0;
  };

  class Monitor {
   public:
    virtual ~Monitor() { }
    virtual bool tryAcquire(Thread* context) = 0;
    virtual void acquire(Thread* context) = 0;
    virtual void release(Thread* context) = 0;
    virtual bool wait(Thread* context, int64_t time) = 0;
    virtual void notify(Thread* context) = 0;
    virtual void notifyAll(Thread* context) = 0;
    virtual Thread* owner() = 0;
    virtual void dispose() = 0;
  };

  class Local {
   public:
    virtual ~Local() { }
    virtual void* get() = 0;
    virtual void set(void* p) = 0;
    virtual void dispose() = 0;
  };

  class Region {
   public:
    virtual ~Region() { }
    virtual const uint8_t* start() = 0;
    virtual size_t length() = 0;
    virtual void dispose() = 0;
  };

  class Library {
   public:
    virtual ~Library() { }
    virtual void* resolve(const char* function) = 0;
    virtual const char* name() = 0;
    virtual bool mapName() = 0;
    virtual Library* next() = 0;
    virtual void dispose() = 0;
  };

  class SignalHandler {
   public:
    virtual ~SignalHandler() { }

    virtual bool handleSignal(void** ip, void** base, void** stack,
                              void** thread) = 0;
  };

  virtual ~System() { }

  virtual bool success(Status) = 0;
  virtual void* tryAllocate(unsigned size, bool executable) = 0;
  virtual void free(const void* p, unsigned size, bool executable) = 0;
  virtual Status attach(Runnable*) = 0;
  virtual Status start(Runnable*) = 0;
  virtual Status make(Mutex**) = 0;
  virtual Status make(Monitor**) = 0;
  virtual Status make(Local**) = 0;
  virtual Status handleSegFault(SignalHandler* handler) = 0;
  virtual uint64_t call(void* function, uintptr_t* arguments, uint8_t* types,
                        unsigned count, unsigned size,
                        unsigned returnType) = 0;
  virtual Status map(Region**, const char* name) = 0;
  virtual FileType identify(const char* name) = 0;
  virtual Status load(Library**, const char* name, bool mapName) = 0;
  virtual char pathSeparator() = 0;
  virtual int64_t now() = 0;
  virtual void exit(int code) = 0;
  virtual void abort() = 0;
  virtual void dispose() = 0;
};

inline void NO_RETURN
abort(System* s)
{
  s->abort(); // this should not return
  ::abort();
}

inline void NO_RETURN
sysAbort(System* s)
{
  abort(s);
}

inline void
expect(System* s, bool v)
{
  if (UNLIKELY(not v)) abort(s);
}

#ifdef NDEBUG

# define assert(a, b)

#else // not NDEBUG

inline void
assert(System* s, bool v)
{
  expect(s, v);
}

#endif // not NDEBUG

System*
makeSystem();

} // namespace vm

#endif//SYSTEM_H
