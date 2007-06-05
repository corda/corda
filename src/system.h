#ifndef SYSTEM_H
#define SYSTEM_H

#include "common.h"

#define ACQUIRE(x) System::MonitorResource MAKE_NAME(monitorResource_) (x)

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
    virtual void acquire() = 0;
    virtual void release() = 0;
    virtual void wait() = 0;
    virtual void notify() = 0;
    virtual void notifyAll() = 0;
    virtual void dispose() = 0;
  };

  class MonitorResource {
   public:
    MonitorResource(Monitor* m): m(m) { m->acquire(); }
    ~MonitorResource() { m->release(); }

   private:
    Monitor* m;
  };

  class File {
   public:
    virtual ~File() { }
    virtual Status read(uint8_t* data, unsigned* size) = 0;
    virtual Status write(const uint8_t* data, unsigned size) = 0;
    virtual Status close() = 0;
  };

  static const int ReadOnly = 00;
  static const int WriteOnly = 01;
  static const int ReadWrite = 02;
  static const int Append = 02000;
  static const int Create = 0100;

  static const int UserRead = 0400;
  static const int UserWrite = 0200;
  static const int UserExecute = 0100;
  static const int GroupRead = 040;
  static const int GroupWrite = 020;
  static const int GroupExecute = 010;
  static const int OtherRead = 04;
  static const int OtherWrite = 02;
  static const int OtherExecute = 01;

  virtual ~System() { }

  virtual bool success(Status) = 0;
  virtual void* allocate(unsigned size) = 0;
  virtual void zero(void*, unsigned size) = 0;
  virtual void free(void*) = 0;
  virtual Status start(Thread*) = 0;
  virtual Status make(Monitor**) = 0;
  virtual Status open(File**, const char* path, int flags, int mode) = 0;
  virtual void NO_RETURN abort() = 0;
};

#endif//SYSTEM_H
