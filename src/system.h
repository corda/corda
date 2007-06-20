#ifndef SYSTEM_H
#define SYSTEM_H

#include "common.h"

namespace vm {

class System {
 public:
  typedef int Status;

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

  virtual ~System() { }

  virtual bool success(Status) = 0;
  virtual void* allocate(unsigned* size) = 0;
  virtual void free(const void*) = 0;
  virtual Status start(Thread*) = 0;
  virtual Status make(Monitor**) = 0;
  virtual void abort() = 0;

  void* allocate(unsigned size) {
    unsigned requested = size;
    void* p = allocate(&size);
    if (size != requested) {
      abort();
    }
    return p;
  }
};

} // namespace vm

#endif//SYSTEM_H
