#include "common.h"
#include "system.h"
#include "constants.h"
#include "machine.h"

using namespace vm;

namespace {

const unsigned FrameThread = 8;
const unsigned FrameMethod = 12;
const unsigned FrameNext = 16;
const unsigned FrameFootprint = 12;

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

  Rope() { }

  Rope(System* s):
    s(s),
    front(0),
    rear(0),
    count(0),
    position(Node::Size)
  { }

  void append(uint8_t v) {
    if (position == Node::Size) {
      if (front == 0 or rear->next == 0) {
        Node* n = new (s->allocate(sizeof(Node))) Node;
        if (front == 0) {
          front = rear = n;
        } else {
          rear->next = n;
          rear = n;
        }
      } else {
        rear = rear->next;
      }
      position = 0;
      ++ count;
    }

    rear->data[position++] = v;
  }

  void append4(uint32_t v) {
    append((v >>  0) & 0xFF);
    append((v >>  8) & 0xFF);
    append((v >> 16) & 0xFF);
    append((v >> 24) & 0xFF);
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

class ArgumentList;

class MyThread: public Thread {
 public:
  MyThread(Machine* m, object javaThread, vm::Thread* parent):
    vm::Thread(m, javaThread, parent),
    argumentList(0),
    frame(0)
  { }

  ArgumentList* argumentList;
  void* frame;
};

class Assembler {
 public:
  class Label {
   public:
    class Snapshot {
     public:
      Rope rope;
      unsigned ip;
    };

    static const unsigned Capacity = 8;

    Label():
      unresolvedCount(0),
      mark_(-1)
    { }

    void reference(Rope* r, unsigned ip) {
      if (mark_ == -1) {
        expect(r->s, unresolvedCount < Capacity);
        unresolved[unresolvedCount].rope = *r;
        unresolved[unresolvedCount].ip = ip;
        ++ unresolvedCount;

        r->append4(0);
      } else {
        r->append4(mark_ - ip);
      }
    }

    void mark(Rope* r) {
      mark_ = r->length();
      for (unsigned i = 0; i < unresolvedCount; ++i) {
        unresolved[i].rope.append4(mark_ - unresolved[i].ip);
      }
    }

    Snapshot unresolved[Capacity];
    unsigned unresolvedCount;
    int mark_;
  };

  enum Register {
    eax = 0,
    ecx = 1,
    edx = 2,
    ebx = 3,
    esp = 4,
    ebp = 5
  };

  Assembler(System* s):
    r(s)
  { }

  void mov(Register src, Register dst) {
    r.append(0x89);
    r.append(0xc0 | (src << 3) | dst);
  }

  void mov(Register src, int srcOffset, Register dst) {
    r.append(0x8b);
    if (srcOffset) {
      r.append(0x40 | (dst << 3) | src);
      r.append(srcOffset);
    } else {
      r.append((dst << 3) | src);
    }
  }

  void mov(Register src, Register dst, int dstOffset) {
    r.append(0x89);
    if (dstOffset) {
      r.append(0x40 | (src << 3) | dst);
      r.append(dstOffset);
    } else {
      r.append((src << 3) | dst);
    }
  }

  void mov(uintptr_t src, Register dst) {
    r.append(0xb8 | dst);
    r.append4(src);
  }

  void push(Register reg) {
    r.append(0x50 | reg);
  }

  void push(Register reg, int offset) {
    r.append(0xff);
    r.append(0x70 | reg);
    r.append(offset);
  }

  void push(int v) {
    r.append(0x6a);
    r.append(v);
  }

  void pop(Register dst) {
    r.append(0x58 | dst);
  }

  void pop(Register dst, int offset) {
    r.append(0x8f);
    r.append(0x40 | dst);
    r.append(offset);
  }

  void add(Register src, Register dst) {
    r.append(0x01);
    r.append(0xc0 | (src << 3) | dst);
  }

  void add(int src, Register dst) {
    r.append(0x83);
    r.append(0xc0 | dst);
    r.append(src);
  }

  void sub(Register src, Register dst) {
    r.append(0x29);
    r.append(0xc0 | (src << 3) | dst);
  }

  void sub(int src, Register dst) {
    r.append(0x83);
    r.append(0xe8 | dst);
    r.append(src);
  }

  void or_(Register src, Register dst) {
    r.append(0x09);
    r.append(0xc0 | (src << 3) | dst);
  }

  void or_(int src, Register dst) {
    r.append(0x83);
    r.append(0xc8 | dst);
    r.append(src);
  }

  void and_(Register src, Register dst) {
    r.append(0x21);
    r.append(0xc0 | (src << 3) | dst);
  }

  void and_(int src, Register dst) {
    r.append(0x83);
    r.append(0xe0 | dst);
    r.append(src);
  }

  void ret() {
    r.append(0xc3);
  }

  void jmp(Label& label) {
    r.append(0xE9);
    label.reference(&r, r.length() + 4);
  }

  void jmp(Register reg) {
    r.append(0xff);
    r.append(0xe0 | reg);
  }

  void jmp(Register reg, int offset) {
    r.append(0xff);
    r.append(0x60 | reg);
    r.append(offset);
  }

  void jnz(Label& label) {
    r.append(0x0F);
    r.append(0x85);
    label.reference(&r, r.length() + 4);
  }

  void jne(Label& label) {
    jnz(label);
  }

  void cmp(int v, Register reg) {
    r.append(0x83);
    r.append(0xf8 | reg);
    r.append(v);
  }

  void call(Register reg) {
    r.append(0xff);
    r.append(0xd0 | reg);
  }

  Rope r;
};

void
compileMethod(Thread* t, object method);

int
localOffset(int v, int parameterFootprint)
{
  v *= 4;
  if (v < parameterFootprint) {
    return v + 8 + FrameFootprint;
  } else {
    return -(v + 4 - parameterFootprint);
  }
}

class Compiler: public Assembler {
 public:
  Compiler(System* s):
    Assembler(s)
  { }

  void compile(Thread* t, object method) {
    push(ebp);
    mov(esp, ebp);

    object code = methodCode(t, method);
    unsigned parameterFootprint = methodParameterFootprint(t, method) * 4;

    // reserve space for local variables
    sub((codeMaxLocals(t, code) * 4) - parameterFootprint, esp);
    
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
        push(ebp, localOffset(0, parameterFootprint));
        break;

      case iload_1:
      case fload_1:
        push(ebp, localOffset(1, parameterFootprint));
        break;

      case iload_2:
      case fload_2:
        push(ebp, localOffset(2, parameterFootprint));
        break;

      case iload_3:
      case fload_3:
        push(ebp, localOffset(3, parameterFootprint));
        break;

      case istore_0:
      case fstore_0:
        pop(ebp, localOffset(0, parameterFootprint));
        break;

      case istore_1:
      case fstore_1:
        pop(ebp, localOffset(1, parameterFootprint));
        break;

      case istore_2:
      case fstore_2:
        pop(ebp, localOffset(2, parameterFootprint));
        break;

      case istore_3:
      case fstore_3:
        pop(ebp, localOffset(3, parameterFootprint));
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

  void compileStub(MyThread* t) {
    unsigned frameOffset = reinterpret_cast<uintptr_t>(&(t->frame))
      - reinterpret_cast<uintptr_t>(t);

    push(ebp);
    mov(esp, ebp);

    mov(ebp, FrameThread, eax);
    mov(ebp, eax, frameOffset);              // set thread frame to current

    push(ebp, FrameMethod);
    push(ebp, FrameThread);
    mov(reinterpret_cast<uintptr_t>(compileMethod), eax);
    call(eax);
    add(8, esp);

    mov(ebp, FrameMethod, eax);
    mov(eax, MethodCompiled, eax);           // load compiled code

    mov(ebp, esp);
    pop(ebp);
    
    add(CompiledBody, eax);
    jmp(eax);                                // call compiled code
  }
};

void
compileMethod(Thread* t, object method)
{
  if (methodCompiled(t, method) == t->m->processor->methodStub(t)) {
    PROTECT(t, method);

    ACQUIRE(t, t->m->classLock);
    
    if (methodCompiled(t, method) == t->m->processor->methodStub(t)) {
      Compiler c(t->m->system);
      c.compile(t, method);
    
      object compiled = makeCompiled(t, 0, c.r.length(), false);
      c.r.copyTo(&compiledBody(t, compiled, 0));
    
      set(t, methodCompiled(t, method), compiled);
    }
  }
}

object
compileStub(Thread* t)
{
  Compiler c(t->m->system);
  c.compileStub(static_cast<MyThread*>(t));
  
  object stub = makeCompiled(t, 0, c.r.length(), false);
  c.r.copyTo(&compiledBody(t, stub, 0));

  return stub;
}

class ArgumentList {
 public:
  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, bool indirectObjects, va_list arguments):
    t(static_cast<MyThread*>(t)),
    next(this->t->argumentList),
    array(array),
    objectMask(objectMask),
    position(0)
  {
    this->t->argumentList = this;

    addInt(reinterpret_cast<uintptr_t>(t));
    addObject(0); // reserve space for method
    addInt(reinterpret_cast<uintptr_t>(this->t->frame));

    if (this_) {
      addObject(this_);
    }

    const char* s = spec;
    ++ s; // skip '('
    while (*s and *s != ')') {
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;

        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;

      case '[':
        while (*s == '[') ++ s;
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          break;

        default:
          ++ s;
          break;
        }

        if (indirectObjects) {
          object* v = va_arg(arguments, object*);
          addObject(v ? *v : 0);
        } else {
          addObject(va_arg(arguments, object));
        }
        break;
      
      case 'J':
      case 'D':
        ++ s;
        addLong(va_arg(arguments, uint64_t));
        break;
          
      default:
        ++ s;
        addInt(va_arg(arguments, uint32_t));
        break;
      }
    }    
  }

  ArgumentList(Thread* t, uintptr_t* array, bool* objectMask, object this_,
               const char* spec, object arguments):
    t(static_cast<MyThread*>(t)),
    next(this->t->argumentList),
    array(array),
    objectMask(objectMask),
    position(0)
  {
    this->t->argumentList = this;

    addInt(0); // reserve space for trace pointer
    addObject(0); // reserve space for method pointer

    if (this_) {
      addObject(this_);
    }

    unsigned index = 0;
    const char* s = spec;
    ++ s; // skip '('
    while (*s and *s != ')') {
      switch (*s) {
      case 'L':
        while (*s and *s != ';') ++ s;
        ++ s;
        addObject(objectArrayBody(t, arguments, index++));
        break;

      case '[':
        while (*s == '[') ++ s;
        switch (*s) {
        case 'L':
          while (*s and *s != ';') ++ s;
          ++ s;
          break;

        default:
          ++ s;
          break;
        }
        addObject(objectArrayBody(t, arguments, index++));
        break;
      
      case 'J':
      case 'D':
        ++ s;
        addLong(cast<int64_t>(objectArrayBody(t, arguments, index++),
                              BytesPerWord));
        break;

      default:
        ++ s;
        addInt(cast<int32_t>(objectArrayBody(t, arguments, index++),
                             BytesPerWord));
        break;
      }
    }
  }

  ~ArgumentList() {
    t->argumentList = next;
  }

  void addObject(object v) {
    array[position] = reinterpret_cast<uintptr_t>(v);
    objectMask[position] = true;
    ++ position;
  }

  void addInt(uint32_t v) {
    array[position] = v;
    objectMask[position] = false;
    ++ position;
  }

  void addLong(uint64_t v) {
    memcpy(array + position, &v, 8);
    objectMask[position] = false;
    objectMask[position] = false;
    position += 2;
  }

  MyThread* t;
  ArgumentList* next;
  uintptr_t* array;
  bool* objectMask;
  unsigned position;
};

object
invoke(Thread* thread, object method, ArgumentList* arguments)
{
  MyThread* t = static_cast<MyThread*>(thread);

  arguments->array[1] = reinterpret_cast<uintptr_t>(method);
  
  const char* s = reinterpret_cast<const char*>
    (&byteArrayBody(t, methodSpec(t, method), 0));
  while (*s and *s != ')') ++s;
  unsigned returnCode = fieldCode(t, s[1]);
  unsigned returnType = fieldType(t, returnCode);

  uint64_t result = cdeclCall
    (&compiledBody(t, methodCompiled(t, method), 0), arguments->array,
     arguments->position * 4, returnType);

  object r;
  switch (returnCode) {
  case ByteField:
  case BooleanField:
  case CharField:
  case ShortField:
  case FloatField:
  case IntField:
    r = makeInt(t, result);
    break;

  case LongField:
  case DoubleField:
    r = makeLong(t, result);
    break;

  case ObjectField:
    r = (result == 0 ? 0 :
         *reinterpret_cast<object*>(static_cast<uintptr_t>(result)));
    break;

  case VoidField:
    r = 0;
    break;

  default:
    abort(t);
  };

  return r;
}

class MyProcessor: public Processor {
 public:
  MyProcessor(System* s):
    s(s),
    stub(0)
  { }

  virtual Thread*
  makeThread(Machine* m, object javaThread, Thread* parent)
  {
    return new (s->allocate(sizeof(MyThread))) MyThread(m, javaThread, parent);
  }

  virtual object
  methodStub(Thread* t)
  {
    if (stub == 0) {
      stub = compileStub(t);
    }
    return stub;
  }

  virtual void
  visitObjects(Thread* t, Heap::Visitor*)
  {
    abort(t);
  }

  virtual uintptr_t
  frameStart(Thread* t)
  {
    abort(t);
  }

  virtual uintptr_t
  frameNext(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual bool
  frameValid(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual object
  frameMethod(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual unsigned
  frameIp(Thread* t, uintptr_t)
  {
    abort(t);
  }

  virtual object*
  makeLocalReference(Thread* t, object)
  {
    abort(t);
  }

  virtual void
  disposeLocalReference(Thread* t, object*)
  {
    abort(t);
  }

  virtual object
  invokeArray(Thread* t, object method, object this_, object arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterCount(t, method) * 2;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list(t, array, objectMask, this_, spec, arguments);
    
    return ::invoke(t, method, &list);
  }

  virtual object
  invokeList(Thread* t, object method, object this_, bool indirectObjects,
             va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));
    
    const char* spec = reinterpret_cast<char*>
      (&byteArrayBody(t, methodSpec(t, method), 0));

    unsigned size = methodParameterCount(t, method) * 2;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, spec, indirectObjects, arguments);

    return ::invoke(t, method, &list);
  }

  virtual object
  invokeList(Thread* t, const char* className, const char* methodName,
             const char* methodSpec, object this_, va_list arguments)
  {
    assert(t, t->state == Thread::ActiveState
           or t->state == Thread::ExclusiveState);

    unsigned size = parameterCount(methodSpec) * 2;
    uintptr_t array[size];
    bool objectMask[size];
    ArgumentList list
      (t, array, objectMask, this_, methodSpec, false, arguments);

    object method = resolveMethod(t, className, methodName, methodSpec);
    if (LIKELY(t->exception == 0)) {
      assert(t, ((methodFlags(t, method) & ACC_STATIC) == 0) xor (this_ == 0));

      return ::invoke(t, method, &list);
    } else {
      return 0;
    }
  }

  virtual void dispose() {
    s->free(this);
  }
  
  System* s;
  object stub;
};

} // namespace

namespace vm {

Processor*
makeProcessor(System* system)
{
  return new (system->allocate(sizeof(MyProcessor))) MyProcessor(system);
}

} // namespace vm

