#include "common.h"
#include "system.h"
#include "constants.h"
#include "machine.h"

using namespace vm;

namespace {

class Rope {
 public:
  class Node {
   public:
    static const unsigned Size = 32;

    Node():
      next(0)
    { }

    Node* next;
    uint8_t data[Size];
  };

  Rope(System* s):
    s(s),
    front(0),
    rear(0),
    count(0),
    position(Node::Size)
  { }

  void append(uint8_t v) {
    if (position == Node::Size) {
      Node* n = new (s->allocate(sizeof(Node))) Node;
      if (front == 0) {
        front = rear = n;
      } else {
        rear->next = n;
        rear = n;
      }
      position = 0;
      ++ count;
    }

    rear->data[position++] = v;
  }

  unsigned length() {
    return (count * Node::Size) + position;
  }

  void copyTo(uint8_t* b) {
    if (front) {
      Node* n = front;
      while (true) {
        if (n == rear) {
          memcpy(b, n->data, position);
          break;
        } else {
          memcpy(b, n->data, Node::Size);
          b += Node::Size;
          n = n->next;
        }
      }
    }
  }

  System* s;
  Node* front;
  Node* rear;
  unsigned count;
  unsigned position;
};

class Assembler {
 public:
  Assembler(System* s):
    rope(s)
  { }

  Rope rope;
};

class Compiler: private Assembler {
 public:
  Compiler(System* s):
    Assembler(s)
  { }

  void compile(Thread* t, object method) {
    push(ebp);
    mov(esp, ebp);

    object code = methodCode(t, method);

    // reserve space for local variables
    sub(codeMaxLocals(t, code), esp);
    
    for (unsigned i = 0; i < codeLength(t, code);) {
      switch (codeBody(t, code, i++)) {
      case iadd:
        pop(eax);
        pop(edx);
        add(eax, edx);
        push(edx);
        break;

      case iconst_m1:
        push(-1);
        break;

      case iconst_0:
        push(0);
        break;

      case iconst_1:
        push(1);
        break;

      case iconst_2:
        push(2);
        break;

      case iconst_3:
        push(3);
        break;

      case iconst_4:
        push(4);
        break;

      case iconst_5:
        push(5);
        break;

      case iload_0:
      case fload_0:
        mov(ebp, 0, eax);
        push(eax);
        break;

      case iload_1:
      case fload_1:
        mov(ebp, -1, eax);
        push(eax);
        break;

      case iload_2:
      case fload_2:
        mov(ebp, -2, eax);
        push(eax);
        break;

      case iload_3:
      case fload_3:
        mov(ebp, -3, eax);
        push(eax);
        break;

      case istore_0:
      case fstore_0:
        pop(eax);
        mov(eax, ebp, 0);
        break;

      case istore_1:
      case fstore_1:
        pop(eax);
        mov(eax, ebp, -1);
        break;

      case istore_2:
      case fstore_2:
        pop(eax);
        mov(eax, ebp, -2);
        break;

      case istore_3:
      case fstore_3:
        pop(eax);
        mov(eax, ebp, -3);
        break;

      case return_:
        mov(ebp, esp);
        pop(ebp);
        ret();
        break;

      default:
        abort(t);
      }
    }
  }
};

object
compile(Thread* t, object method)
{
  Compiler c(t->vm->system);
  c.compile(t, method);
  
  object r = makeByteArray(t, c.rope.length(), false);
  c.rope.copyTo(&byteArrayBody(t, r, 0));

  return r;
}

class MyInvoker: public Invoker {
 public:
  MyInvoker(System* s):
    s(s)
  { }

  virtual object
  invokeArray(Thread* t, object method, object this_, object arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

    uintptr_t a[methodParameterCount(t, method) * 2];
    ArgumentArray array(t, a, method, this_, spec, arguments);
    
    return invoke(t, method, &array);
  }

  virtual object
  invokeList(Thread* t, object method, object this_, bool indirectObjects,
             va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));
    
    uintptr_t a[methodParameterCount(t, method) * 2];
    ArgumentArray array(t, a, method, this_, spec, indirectObjects, arguments);

    return invoke(t, method, &array);
  }

  virtual object
  invokeList(Thread* t, const char* className, const char* methodName,
             const char* methodSpec, object this_, va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    uintptr_t a[methodParameterCount(t, method) * 2];
    ArgumentArray array(t, a, method, this_, methodSpec, false, arguments);

    object method = resolveMethod(t, className, methodName, methodSpec);
    if (LIKELY(t->exception == 0)) {
      assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

      return invoke(t, method, &array);
    } else {
      return 0;
    }
  }

  virtual void dispose() {
    s->free(this);
  }
  
  System* s;
};

} // namespace

namespace vm {

Invoker*
makeInvoker(System* system)
{
  return new (system->allocate(sizeof(MyInvoker))) MyInvoker(system);
}

} // namespace vm
