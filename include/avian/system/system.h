/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef SYSTEM_H
#define SYSTEM_H

#include "avian/common.h"
#include <avian/util/allocator.h>
#include <avian/util/abort.h>

namespace vm {

class System : public avian::util::Aborter {
 public:
  typedef intptr_t Status;

  enum FileType { TypeUnknown, TypeDoesNotExist, TypeFile, TypeDirectory };

  class Thread {
   public:
    virtual void interrupt() = 0;
    virtual bool getAndClearInterrupted() = 0;
    virtual void join() = 0;
    virtual void dispose() = 0;
  };

  class ThreadVisitor {
   public:
    virtual void visit(void* ip, void* stack, void* link) = 0;
  };

  class Runnable {
   public:
    virtual void attach(Thread*) = 0;
    virtual void run() = 0;
    virtual bool interrupted() = 0;
    virtual void setInterrupted(bool v) = 0;
  };

  class Mutex {
   public:
    virtual void acquire() = 0;
    virtual void release() = 0;
    virtual void dispose() = 0;
  };

  class Monitor {
   public:
    virtual bool tryAcquire(Thread* context) = 0;
    virtual void acquire(Thread* context) = 0;
    virtual void release(Thread* context) = 0;
    virtual void wait(Thread* context, int64_t time) = 0;
    virtual bool waitAndClearInterrupted(Thread* context, int64_t time) = 0;
    virtual void notify(Thread* context) = 0;
    virtual void notifyAll(Thread* context) = 0;
    virtual Thread* owner() = 0;
    virtual void dispose() = 0;
  };

  class Local {
   public:
    virtual void* get() = 0;
    virtual void set(void* p) = 0;
    virtual void dispose() = 0;
  };

  class Region {
   public:
    virtual const uint8_t* start() = 0;
    virtual size_t length() = 0;
    virtual void dispose() = 0;
  };

  class Directory {
   public:
    virtual const char* next() = 0;
    virtual void dispose() = 0;
  };

  class Library {
   public:
    virtual void* resolve(const char* symbol) = 0;
    virtual const char* name() = 0;
    virtual Library* next() = 0;
    virtual void setNext(Library* lib) = 0;
    virtual void disposeAll() = 0;
  };

  class MonitorResource {
   public:
    MonitorResource(System::Thread* t, System::Monitor* m) : t(t), m(m)
    {
      m->acquire(t);
    }

    ~MonitorResource()
    {
      m->release(t);
    }

   private:
    System::Thread* t;
    System::Monitor* m;
  };

  virtual bool success(Status) = 0;
  virtual void* tryAllocate(size_t sizeInBytes) = 0;
  virtual void free(const void* p) = 0;
  virtual Status attach(Runnable*) = 0;
  virtual Status start(Runnable*) = 0;
  virtual Status make(Mutex**) = 0;
  virtual Status make(Monitor**) = 0;
  virtual Status make(Local**) = 0;

  virtual Status visit(Thread* thread, Thread* target, ThreadVisitor* visitor)
      = 0;

  virtual Status map(Region**, const char* name) = 0;
  virtual FileType stat(const char* name, size_t* length) = 0;
  virtual Status open(Directory**, const char* name) = 0;
  virtual const char* libraryPrefix() = 0;
  virtual const char* librarySuffix() = 0;
  virtual Status load(Library**, const char* name) = 0;
  virtual char pathSeparator() = 0;
  virtual char fileSeparator() = 0;
  virtual const char* toAbsolutePath(avian::util::AllocOnly* allocator,
                                     const char* name) = 0;
  virtual int64_t now() = 0;
  virtual void yield() = 0;
  virtual void exit(int code) = 0;
  virtual void dispose() = 0;
};

inline void* allocate(System* s, size_t size)
{
  void* p = s->tryAllocate(size);
  if (p == 0)
    s->abort();
  return p;
}

#define ACQUIRE_MONITOR(t, m) \
  System::MonitorResource MAKE_NAME(monitorResource_)(t, m)

inline avian::util::Aborter* getAborter(System* s)
{
  return s;
}

inline void NO_RETURN sysAbort(System* s)
{
  abort(s);
}

// #ifdef NDEBUG

// # define assertT(a, b)
// # define vm_assert(a, b)

// #else // not NDEBUG

// inline void
// assertT(System* s, bool v)
// {
//   expect(s, v);
// }

// # define vm_assert(a, b) vm::assertT(a, b)

// #endif // not NDEBUG

AVIAN_EXPORT System* makeSystem(bool reentrant = false);

}  // namespace vm

#endif  // SYSTEM_H
