#include "machine.h"

namespace vm {

class HeapMap {
 public:
  virtual int find(object value) = 0;
  virtual void dispose() = 0;
};

class HeapWalker {
 public:
  virtual void root() = 0;
  virtual unsigned visitNew(object value) = 0;
  virtual void visitOld(object value, unsigned number) = 0;
  virtual void push(unsigned offset) = 0;
  virtual void pop() = 0;
};

HeapMap*
walk(Thread* t, HeapWalker* w);

} // namespace vm
